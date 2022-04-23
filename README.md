# Links

Parser generator for JVM.

`Links` uses generalized [SLR](https://en.wikipedia.org/wiki/Simple_LR_parser) parsing algorithm.
SLR enables O(N) parsing for conflict-free grammars. Whenever a grammar contains
a conflict, `Links` delays its resolution to runtime where the parsing algorithm
forks, that is clones the parsing stack and runs two parsing processes
concurrently until one of them succeds.

# Features

* LR(0) parser generator
* SLR(1) lookahead generator
* Generalized parsing (not implemented)