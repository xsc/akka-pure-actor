package org.xsc.pure

import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import akka.actor.{ Props, ActorSystem, ActorLogging, PoisonPill }
import akka.testkit.{ ImplicitSender, TestKit }
import scala.concurrent.duration._
import org.xsc.pure.PureLogic._

// ## Test Actor

object PersistentCounter {
  def props(initialValue: Int): Props =
    Props(new PersistentCounter(initialValue))
}

class PersistentCounter(initialValue: Int)
extends PurePersistentActor[State, Command, Effect, String]
with ActorLogging
{
  val persistenceId = "counter"
  def initial() = State(initialValue)
  def handler() = new Handler()

  override def wrapReceiveCommand(handler: Command => Unit): Receive = {
    case command: Command =>
      log.info(s"command received: $command")
      handler(command)
  }

  override def wrapReceiveRecover(handler: Effect => Unit): Receive = {
    case effect: Effect =>
      log.info(s"recovering effect: $effect")
      handler(effect)
  }
}

// ## Tests

class PurePersistentActorSpec
  extends TestKit(ActorSystem("PurePersistentActorSpec"))
  with Matchers
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def newActor(initialValue: Int) =
    system.actorOf(PersistentCounter.props(initialValue))

  "A pure PersistentCounter actor" when {
    "receiving a Double command" should {
      lazy val actor = newActor(5)
      "double the current value" in {
        actor ! Double
        actor ! Double
        actor ! PureActor.ProbeState
        expectMsg(10000.millis, State(20))
        actor ! PoisonPill
      }

      "retain it after restart" in {
        lazy val sameActor = newActor(5)
        sameActor ! PureActor.ProbeState
        expectMsg(10000.millis, State(20))
      }
    }

    "receiving a failing command" should {
      lazy val actor = newActor(0)
      "return the failure to the sender" in {
        actor ! Fail
        expectMsg(10000.millis, "It failed.")
      }
    }
  }
}
