package com.ibm.analytics.messagehub

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.{Matchers, WordSpec}
import spray.json.{JsNumber, JsObject}

import scala.concurrent._
import scala.concurrent.duration._


class PubSubSpec extends WordSpec with Matchers with ScalaFutures {
  implicit val system = ActorSystem("ml-kafka-client-test")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  /* MessageHub-MokshaTest (AA_KRK/dev) */
  val VCAP = scala.io.Source.fromURL(getClass.getResource("/messagehub.vcap")).mkString

  val topic = "ml-kafka-client-test-2"
  val msgTimeout = PatienceConfiguration.Timeout(600 seconds)
  val msgCount = 10
  var admin: Admin = null
  var publisher: Publisher = null
  var subscriber: Subscriber = null

  implicit val akkaPatience = PatienceConfig(scaled(10 seconds), scaled(100 millis))

  "Admin" should {
    "be created from VCAP" in {
      admin = Admin.create(VCAP).get
    }

    s"create topic $topic" in {
      admin.createTopic(topic, 1 hour, 4).futureValue
    }

    "should list topic" in {
      admin.getTopics.futureValue.map(_.name) should contain(topic)
    }
  }

  "Publisher" should {
    "be created from VCAP" in {
      publisher = Publisher.create(VCAP).get
    }

    s"publish $msgCount messages" in {
      val source = Source(1 to msgCount)
        .map(_.toString)
        .map { elem =>
          new ProducerRecord[String, String](topic,
            JsObject(
              "metric" -> JsNumber(scala.util.Random.nextInt(1000)),
              "iteration" -> JsNumber(elem)
            ).toString
          )
        }
      publisher.publish(source).futureValue(msgTimeout)
    }
  }

  "Subscriber" should {
    "be created from VCAP" in {
      subscriber = Subscriber.create(VCAP, "test-group-1").get
    }

    s"consume $msgCount messages" in {
      var count = 0
      val p = Promise[Int]()

      subscriber.subscribe(topic) { _ =>
        count += 1
        if (count == msgCount) {
          p success count
        }
        Future.successful(Done)
      }
      p.future.futureValue(msgTimeout)
    }

    "stop listening" in {

    }
  }

  "Admin" should {
    s"delete topic $topic" in {
      admin.deleteTopic(topic).futureValue
    }

    "should not list topic" in {
      admin.getTopics.futureValue should not contain (topic)
    }
  }
}