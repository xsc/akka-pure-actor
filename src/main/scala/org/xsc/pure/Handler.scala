package org.xsc.pure

/*
 * Generic trait for asynchronous execution of impure logic (commands),
 * producing a set of pure state effects.
 */
trait Handler[State <: PureState[Effect, State], Command, Effect, Response] {
  type Result = (Option[Response], List[Effect])
  def handle(state: State, command: Command): Result

  protected def onlyEffects(effects: Effect*) =
    (None, effects.toList)

  protected def onlyResponse(response: Response) =
    (Some(response), List.empty)
}
