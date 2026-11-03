# Shake Race

![Shake Race's robot demo, four runners heading for the finish](../images/shakerace-demo.png)

A running race where the Wiimote is your legs. Everyone gets a lane, and the
harder you shake, the faster your runner goes. Stop shaking and you slow
down. First across the line wins, and the remote gives a long buzz when you
finish first.

**Wear the wrist straps for this one.** It's the game most likely to send a
Wiimote into the TV.

## How to play

1. Start **Shake Race** from the Homebrew Channel.
2. Press **A** to join. Up to four players, one lane each.
3. Wait for **GO!** Shaking during the 3-2-1 does nothing.
4. Shake! The bar at the left of your lane shows how fast you're going.
5. Once someone finishes, everyone else has 8 more seconds. A race also ends
   by itself after 60 seconds, in case everyone stops.
6. The results list everyone in order: finishers by time, the rest by how far
   they got. Press A for a rematch with the same players. Others can still
   join during the countdown.

**HOME** quits. If nobody presses anything on the title for 8 seconds, four
robot runners race in a demo.

## Playing it on the Mac

```sh
bb play:shakerace
```

Left click joins, and **holding the space bar** shakes the remote (so does
the middle mouse button). Return is HOME. A person hasn't played it this way
yet. The scripted test below shakes through the same Dolphin control, but the
space bar mapping itself is untried.

## How it's built

`examples/shakerace/game.c` is the rules, with 35 checks in
`examples/shakerace/test/test_game.c`. They cover: a still remote doesn't run,
a steady shake settles at the speed the header documents, the speed cap,
slowing down, finishing order, the grace time, the time limit, ties, and
rematches. Another check makes sure shaking on the results screen can't move
a runner who didn't finish. An early version allowed it, so the standings
could change after the race was over.

The speed model, from `game.h`:

- Every frame, a shake above the dead zone (0.25 g) adds speed:
  `speed += 0.5 * (shake - 0.25)`.
- Speed drains away at 1.6 per second, and tops out at 15 m/s.
- So steady shaking of `s` g per frame settles at about
  `0.5 * (s - 0.25) * 60 / 1.6` m/s.

Dolphin's scripted shake averages 0.85 g per frame, and its runner finishes
the 100 m in about 10 seconds. How hard a real seven-year-old shakes is the
open question. Those three numbers are the ones to tune on the console, and
[Wiimote input](wiimote-input.md) has the measurements behind them.

## Ideas for next time

- Hurdles: press A at the right moment to jump.
- Pick your runner: bunny, cat, robot.
- A relay, where two players on a team share a lane.
