package org.xsc.pure

import akka.persistence.{ PersistentActor, AtLeastOnceDelivery }

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

  private def receiveDelivery: Receive = {
    case confirmation @ ConfirmedEffect(deliveryId) =>
      persist(confirmation) { _ => confirmDelivery(deliveryId) }
    case ConfirmableEffect(deliveryId, effect) =>
      dispatchEffect(effect) { effect =>
        propagateEffect.lift(effect)
        self ! ConfirmedEffect(deliveryId)
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
    wrapReceive(receiveAction, mutateState)
      .orElse(receiveDelivery)
      .orElse(receiveInternal)

  private def generateReceiveRecover() =
    wrapReceive(noOp, mutateState)
      .orElse(receiveDeliveryRecover)

  override def receiveCommand: Receive = generateReceiveCommand()
  override def receiveRecover: Receive = generateReceiveRecover()
}
