package org.xsc.pure

import akka.persistence.{ PersistentActor, AtLeastOnceDelivery }
import concurrent.Future

object PurePersistentActor {
  trait Ephemeral

  final case class EphemeralEffect(effect: Any)
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

  // ---- Dispatching
  private def dispatchEffect(message: Any)(to: Effect => Unit) = {
    wrapReceive(noOp, to)(message)
  }

  private def dispatchPropagation(message: Any)(to: Future[Unit] => Unit) = {
    dispatchEffect(message) { effect =>
      val propagation =
        propagateEffect
          .lift(effect)
          .getOrElse(Future.successful(()))
      to(propagation)
    }
  }

  // ---- State
  private var state = initialState
  private def mutateState(effect: Effect): Unit = {
    state = updateState(state, effect)
    deliverEffect(effect)
  }

  private def persistEffect(effect: Effect): Unit = {
    effect match {
      case _: Ephemeral => deferAsync(effect)(mutateState)
      case _ => persist(effect)(mutateState)
    }
  }

  private def persistEffects(effects: Seq[Effect]): Unit = {
    effects.foreach(persistEffect)
  }

  // ---- AtLeastOnceDelivery
  // We're delivering the effect to ourselves, allowing us to replay unfinished
  // effects after recovery.
  private def deliverEffect(effect: Effect) = {
    effect match {
      case _: Ephemeral => self ! EphemeralEffect(effect)
      case _ => deliver(self.path)(id => ConfirmableEffect(id, effect))
    }
  }

  private def confirmEffectOnComplete(deliveryId: Long)(f: Future[Unit]) = {
    val me = self
    f.onComplete{ _ => me.tell(ConfirmedEffect(deliveryId), me) }
    ()
  }

  private def receiveDelivery: Receive = {
    case confirmation @ ConfirmedEffect(deliveryId) =>
      persist(confirmation) { _ => confirmDelivery(deliveryId) }
    case EphemeralEffect(effect) =>
      dispatchPropagation(effect)(_ => ())
    case ConfirmableEffect(deliveryId, effect) =>
      dispatchPropagation(effect)(confirmEffectOnComplete(deliveryId))
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
    persistEffects(effects)
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
