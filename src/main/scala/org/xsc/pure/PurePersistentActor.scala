package org.xsc.pure

import akka.actor.ActorRef
import akka.persistence.{ PersistentActor, AtLeastOnceDelivery }
import concurrent.Future

object PurePersistentActor {
  final case class ConfirmableEffect(deliveryId: Long, effect: Any)
  final case class ConfirmedEffect(deliveryId: Long)

  private def noOp (v: Any) = ()
}

trait PurePersistentActor[Action, Effect, Response, State]
    extends PersistentActor
    with AtLeastOnceDelivery
    with PureActor.Base[Action, Effect, Response, State] {
  import PurePersistentActor._
  import context.dispatcher

  // ---- State
  private var state = initialState
  private def mutateState(effect: Effect): Unit = {
    state = updateState(state, effect)
    deliverIfPropagatable(effect)
  }

  // ---- AtLeastOnceDelivery
  // We're delivering the effect to ourselves, allowing us to replay unfinished
  // effects after recovery.
  private def deliverIfPropagatable(effect: Effect) = {
    if (propagateEffect.isDefinedAt(effect)) {
      deliver(self.path)(id => ConfirmableEffect(id, effect))
    }
  }

  private def dispatchEffect(message: Any)(to: Effect => Unit) = {
    wrapReceive(noOp, to)(message)
  }

  private def confirmEffect(me: ActorRef, deliveryId: Long) = {
    me.tell(ConfirmedEffect(deliveryId), me)
  }

  private def confirmEffectOnCompletion(me: ActorRef, deliveryId: Long)(f: Future[Unit]) = {
    f.onComplete(_ => confirmEffect(me, deliveryId))
    ()
  }

  private def receiveDelivery: Receive = {
    case confirmation @ ConfirmedEffect(deliveryId) =>
      persist(confirmation) { _ => confirmDelivery(deliveryId) }
    case ConfirmableEffect(deliveryId, effect) =>
      val me = self
      dispatchEffect(effect) { effect =>
        propagateEffect
          .lift(effect)
          .map(confirmEffectOnCompletion(me, deliveryId))
          .getOrElse(confirmEffect(me, deliveryId))
      }
  }

  private def receiveDeliveryRecover: Receive = {
    case ConfirmedEffect(deliveryId) =>
      confirmDelivery(deliveryId)
  }

  // ---- Internal Messages
  private def receiveInternal: Receive = {
    case PureActor.ProbeState => deferAsync(state)(sender() ! _)
  }

  // ---- Action Handling
  private def receiveAction(action: Action): Unit = {
    val (maybeResponse, effects) = handleAction(state, action)
    persistAll(effects)(mutateState)
    respondToSender(maybeResponse)
  }

  private def respondToSender(maybeResponse: Option[Response]) = {
    maybeResponse.foreach { response =>
      deferAsync(response)(sender() ! _)
    }
  }

  // ---- Receive/Recover Logic
  private def generateReceiveCommand() =
    receiveDelivery
      .orElse(wrapReceive(receiveAction, mutateState))
      .orElse(receiveInternal)

  private def generateReceiveRecover() =
    wrapReceive(noOp, mutateState)
      .orElse(receiveDeliveryRecover)

  override def receiveCommand: Receive = generateReceiveCommand()
  override def receiveRecover: Receive = generateReceiveRecover()
}
