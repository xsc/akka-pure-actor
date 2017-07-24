package org.xsc.pure

import akka.persistence.PersistentActor

trait PurePersistentActor[Action, Effect, Response, State]
    extends PersistentActor
    with PureActor.Base[Action, Effect, Response, State] {

  // ---- State
  private var state = initialState
  private def mutateState(effect: Effect): Unit = {
    state = updateState(state, effect)
  }

  // ---- Action/Effect Handling
  private def receiveAction(action: Action): Unit = {
    val (maybeResponse, effects) = handleAction(state, action)
    persistAll(effects)(mutateState)
    maybeResponse.foreach { response =>
      deferAsync(response)(sender() ! _)
    }
    forwardEffects(effects)
  }

  private def forwardEffects(effects: List[Effect]): Unit = {
    effects.foreach { effect =>
      deferAsync(effect)(self.forward(_))
    }
  }

  private def receiveEffect(effect: Effect): Unit = {
    propagateEffect.lift(effect)
  }

  private def receiveInternal: Receive = {
    case PureActor.ProbeState => deferAsync(state)(sender() ! _)
  }

  // ---- Receive/Recover Logic
  private def generateReceiveCommand() =
    wrapReceive(receiveAction, receiveEffect).orElse(receiveInternal)

  private def generateReceiveRecover() =
    wrapReceive(_ => (), mutateState)

  override def receiveCommand: Receive = generateReceiveCommand()
  override def receiveRecover: Receive = generateReceiveRecover()
}
