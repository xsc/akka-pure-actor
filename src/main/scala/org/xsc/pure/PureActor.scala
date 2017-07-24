package org.xsc.pure

import akka.actor.Actor

object PureActor {
  trait Base[Action, Effect, Response, State] {
    type Propagate = PartialFunction[Effect, Unit]

    val initialState:    State
    val handleAction:    (State, Action) => (Option[Response], List[Effect])
    val updateState:     (State, Effect) => State
    val propagateEffect: Propagate

    protected def wrapReceive(receiveAction: Action => Unit,
                              receiveEffect: Effect => Unit): Actor.Receive
  }

  final object ProbeState
}

trait PureActor[Action, Effect, Response, State]
    extends Actor
    with PureActor.Base[Action, Effect, Response, State] {

  // ---- Action/Effect Handling
  private def receiveAction(state: State)(action: Action): Unit = {
    val (maybeResponse, effects) = handleAction(state, action)
    val newState = effects.foldLeft(state)(updateState)
    context.become(generateReceive(newState))
    maybeResponse.foreach(sender() ! _)
    forwardEffects(effects)
  }

  private def forwardEffects(effects: List[Effect]): Unit = {
    effects.foreach(self.forward(_))
  }

  private def receiveEffect(effect: Effect): Unit = {
    propagateEffect.lift(effect)
    ()
  }

  private def receiveInternal(state: State): Receive = {
    case PureActor.ProbeState => sender() ! state
  }

  // ---- Receive/Recover Logic
  override def receive: Receive = generateReceive(initialState)

  private def generateReceive(state: State): Receive = {
    wrapReceive(receiveAction(state), receiveEffect)
      .orElse(receiveInternal(state))
  }
}
