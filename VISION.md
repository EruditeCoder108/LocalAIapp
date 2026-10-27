# Where Ember could go next

This is a direction document, not a list of shipped features or a release schedule. The public app currently offers only Bonsai 2B and 4B as chat models.

## Memory that follows meaning

- Evaluate the text-only 270M EmbeddingGemma 2 encoder for local semantic search over saved facts and selected past-chat passages.
- Compare it with a smaller embedding baseline using English, Hindi and Hinglish questions. Measure retrieval quality, peak RAM and time added before a reply on real phones.
- Store facts, vectors, timestamps and source-conversation references locally. Retrieve a small amount of relevant context rather than replaying the full archive.
- Make memory editable, pinnable and removable. Handle changed preferences and conflicting facts explicitly. Keep model suggestions separate from user-confirmed facts.
- Index only new or changed content. Work during active use and offer clear controls over which chats can be recalled.

Embeddings help find information; they do not change the chat model's weights. The current memory implementation is explicit and uses lexical matching.

## Useful tools before more large models

- Start with a bounded calculator and structured data checks.
- Explore constrained Python execution separately; writing a script is different from executing it.
- Improve document rendering while preserving the existing native export path.
- Keep routing and tool execution in application code with bounded requests, cancellation and visible outcomes.

## Verification with clear scope

Validate calculations with the calculator, JSON against a schema, and code against actual executed tests where execution is available. Report exactly what was checked. General prose and citation support cannot be universally certified by these checks.

## Efficient active use

Explore an idle-unload timer, better context management and hardware-adaptive settings. Keep the interface light. Evaluate speculative decoding only if a compatible runtime and real device measurements show an end-to-end improvement.

## Evidence for each change

Maintain fixed retrieval and tool test sets. Compare against the shipped baseline on correctness, latency including preparation, peak memory and data preservation. Record regressions as well as improvements. Experimental multi-model reasoning belongs in separate evaluations before becoming an app feature.
