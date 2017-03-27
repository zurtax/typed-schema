package ru.tinkoff

import java.util.ResourceBundle

import akka.http.scaladsl.server.{RequestContext, Route, Directives}
import Directives._
import ru.tinkoff.tschema.utils.json.CirceSupport._
import io.circe.generic.JsonCodec
import ru.tinkoff.tschema.FromQueryParam
import ru.tinkoff.tschema.akkaHttp.ServeMiddle
import ru.tinkoff.tschema.limits._
import ru.tinkoff.tschema.macros.NamedImpl
import ru.tinkoff.tschema.akkaHttp._
import ru.tinkoff.tschema.swagger.SwaggerTypeable._
import ru.tinkoff.tschema.swagger._
import ru.tinkoff.tschema.syntax._
import ru.tinkoff.tschema.typeDSL._
import shapeless.{Witness ⇒ W, _}
import shapeless.record.Record

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
object TestModule {

  @JsonCodec case class StatsRes(mean: BigDecimal, disperse: BigDecimal, median: BigDecimal)
  @JsonCodec case class Combine(source: CombSource, res: CombRes)
  @JsonCodec case class CombSource(x: Int, y: Int)
  @JsonCodec case class CombRes(mul: Int, sum: Int)

  case class Client(value: Int)

  implicit lazy val statResTypeable = genNamedTypeable[StatsRes]("Stats")
  implicit lazy val combSourceTypeable = genNamedTypeable[CombSource]("CombSource")
  implicit lazy val combResTypeable = genNamedTypeable[CombRes]("CombRes")
  implicit lazy val combineTypeable = genNamedTypeable[Combine]("Combine")

  implicit lazy val clientFromParam = FromQueryParam.intParam.map(Client)
  implicit lazy val clientSwaggerParam = AsSwaggerParam[Client](SwaggerIntValue())

  implicit lazy val bundle = ResourceBundle.getBundle("swagger")

  def concat = operation('concat) :> queryParam[String]('left) :> queryParam[String]('right) :> Get[String]

  def combine = operation('combine) :> capture[Int]('y) :> Get[Combine]

  def sum = operation('sum) :> capture[Int]('y) :> (limit(1) / minute ! 'x) :> Get[Int]

  def stats = operation('stats) :> ReqBody[Vector[BigDecimal]] :> Post[StatsRes]

  def intops = queryParam[Client]('x) :> (combine <|> sum)

  def api = tagPrefix('test) :> (concat <|> intops <|> stats)

  val handler = new {
    def concat(left: String, right: String) = left + right

    def combine(x: Client, y: Int) = Combine(CombSource(x.value, y), CombRes(mul = x.value * y, sum = x.value + y))

    def sum(x: Client, y: Int): Future[Int] = Future(x.value + y)

    def stats(body: Vector[BigDecimal]) = {
      val mean = body.sum / body.size
      val mid = body.size / 2
      val median = if (body.size % 2 == 1) body(mid) else (body(mid) + body(mid - 1)) / 2
      val std = body.view.map(x ⇒ x * x).sum / body.size - mean * mean
      StatsRes(mean, std, median)
    }
  }

  implicit val limitHandler = LimitHandler.trieMap

  val pp = keyPrefix('test) :> queryParam[String]('left) :> queryParam[String]('right) :> (limit(1) / hour ! 'left) :> Get[String]

  pp.serve

  val swagger = api.mkSwagger

  val srv = api.serve

  val impl = NamedImpl[handler.type, srv.Input]

  (limit(10) / hour).serveMiddle('combine)

//  val u: ServeMiddle[Limit[HNil, Rate[W.`1`.T, minute.type]], HNil, W.`'combine`.T] = limitMiddleware[HNil, Limit[HNil, Rate[W.`1`.T, minute.type]], W.`'combine`.T]

  def main(args: Array[String]): Unit = {
    import io.circe.syntax._

    println(impl.description)
    println(srv)
    "asdsad".split('a')
    println(route)
  }

  lazy val route: Route = api.route(handler)

  implicit val encoding = Direct('encoding)(extract(_.request.encoding))

  encoding.servePrefix
}

