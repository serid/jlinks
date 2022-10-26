# JLinks

Parser generator for JVM.

`JLinks` uses generalized [SLR](https://en.wikipedia.org/wiki/Simple_LR_parser) parsing algorithm. SLR enables O(N)
parsing for conflict-free grammars. Whenever a grammar contains a conflict, `JLinks` delays its resolution to runtime
where the parsing algorithm forks, that is clones the parsing stack and runs two parsing processes concurrently until
one of them succeds.

# Features

* LR(0) parser generator
* SLR(1) lookahead generator
* Generalized parsing

# Examples

For examples see the [tests directory](https://github.com/serid/jlinks/tree/master/src/test/kotlin/jitrs/links).

Also check out [this example](https://github.com/serid/jlinks/tree/master/src/main/kotlin/jitrs/algorithmj),
a type inference algorithm using JLinks to parse input expressions. 
