# Harness² Iteration Loop

How the agent improves after every iteration in this project.

## The loop

Plan -> Implement -> Gate -> Contrast -> Reflect -> Adopt

- **Plan**: the issue tracker under `.scratch/<feature>/issues/`
- **Implement**: domain code plus tests
- **Gate**: `python3 tools/harness_gate.py --feature <feature> --hypothesis "..."`
- **Contrast**: `runs/<feature>/iter-NN/telemetry.json` and the diff image
- **Reflect**: fill `runs/<feature>/iter-NN/ledger.md`
- **Adopt**: lesson / guardrail / architecture note / skill / accepted baseline

## Artifacts

```text
runs/
  baselines/<shot>.png              accepted reference frames
  <feature>/iter-NN/telemetry.json  machine readable results
  <feature>/iter-NN/ledger.md       hypothesis, probes, verdict
  <feature>/iter-NN/shots/          captures and diff images
```

## Where an improvement is stored

| Kind of learning | Store |
|------------------|-------|
| A gotcha that can bite again | `LESSONS.md` (Cause/Do/Don't) |
| A rule for future work | `AGENTS.md` (the agent instructions file) |
| A changed contract | `DESIGN.md` or an ADR under `docs/adr/` |
| A repeatable procedure | a skill in `.agents/skills/` |
| The accepted look of the product | `runs/baselines/` |
