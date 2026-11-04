package com.sarvesh173.talos.ui.richtext

/**
 * Ordered segment model produced by [LatexSegmenter.segment].
 *
 * The list preserves message order: concatenating Plain text and the
 * (re-delimited) TeX / code of the other segments reproduces the input
 * modulo the delimiters themselves and the `\$` escape.
 */
sealed interface Segment {
    /** Literal text, including any unmatched / degraded delimiters. */
    data class Plain(val text: String) : Segment

    /** `$...$` or `\(...\)` mathematics, delimiters stripped. */
    data class InlineMath(val tex: String) : Segment

    /** `$$...$$` or `\[...\]` mathematics, delimiters stripped. */
    data class BlockMath(val tex: String) : Segment

    /**
     * Fenced ``` block. [lang] is the info-string language ("" if none),
     * [code] is the raw content between the opening line and the closing
     * fence, without the trailing newline that precedes the closing fence.
     */
    data class CodeBlock(val lang: String, val code: String) : Segment
}

/**
 * Pure-Kotlin (no Android types) message segmenter. Fully unit-testable
 * on the JVM; mirrored 1:1 by tests/test_latex_segmenter.py.
 *
 * Recognised, in priority order at each position:
 *  1. Fenced code blocks ```lang ... ``` — only at a line start
 *     (start of input or immediately after '\n'). A `$` inside a code
 *     block is therefore never maths.
 *  2. Escaped dollar `\$` -> literal '$' in Plain text.
 *  3. `\(` ... `\)` inline maths and `\[` ... `\]` block maths.
 *  4. `$$...$$` block maths — only at a boundary: start of input, an
 *     empty Plain buffer (i.e. immediately after a previous segment),
 *     after whitespace, or after an opening punctuation '(', '[', '{'.
 *     A `$$` inside inline maths opens a block only if it is at such a
 *     boundary (preceded by whitespace inside the inline maths); if it
 *     is not at a boundary it closes the inline maths instead, so
 *     `$a$$b$` is two adjacent inline segments.
 *  5. `$...$` inline maths. The closing '$' must not be escaped, must
 *     not form a `$$` pair (see 4), and the content must not contain a
 *     paragraph break (blank line); a blank line aborts the inline
 *     maths so a lone '$' cannot swallow later paragraphs.
 *
 * Degradation: any unmatched delimiter (`$`, `$$`, `\(`, `\[`, opening
 * ``` fence) degrades to Plain text — the delimiter characters are kept
 * in the Plain text and scanning resumes right after them, so the rest
 * of the message is never swallowed. When a search proves no closing
 * delimiter exists in the remainder, the remainder is emitted as a
 * single Plain segment (keeps the scan linear).
 *
 * Complexity: O(n) in the length of the input. Every search either
 * succeeds and advances past the closing delimiter, or fails once and
 * terminates the scan, so pathological input such as 10_000 '$'
 * characters completes in linear time.
 */
object LatexSegmenter {

    fun segment(text: String): List<Segment> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<Segment>()
        val plain = StringBuilder()
        val n = text.length
        var i = 0

        fun flushPlain() {
            if (plain.isNotEmpty()) {
                out.add(Segment.Plain(plain.toString()))
                plain.clear()
            }
        }

        while (i < n) {
            when {
                // 1. Fenced code block, only at a line start.
                text.startsWith("```", i) && (i == 0 || text[i - 1] == '\n') -> {
                    val lineEnd = text.indexOf('\n', i)
                    val openingLineEnd = if (lineEnd == -1) n else lineEnd
                    val info = text.substring(i + 3, openingLineEnd).trim()
                    val lang = info.takeWhile { it.isLetterOrDigit() || it == '_' || it == '+' || it == '-' }
                    val searchFrom = if (lineEnd == -1) n else lineEnd + 1
                    val closeIdx = if (searchFrom <= n) text.indexOf("```", searchFrom) else -1
                    if (closeIdx == -1) {
                        // Unmatched fence degrades to plain; never swallow as code.
                        plain.append(text.substring(i))
                        i = n
                    } else {
                        var code = text.substring(searchFrom, closeIdx)
                        if (code.endsWith("\r\n")) code = code.dropLast(2)
                        else if (code.endsWith("\n") || code.endsWith("\r")) code = code.dropLast(1)
                        flushPlain()
                        out.add(Segment.CodeBlock(lang, code))
                        i = closeIdx + 3
                    }
                }

                // 2. Escaped dollar.
                text.startsWith("\\$", i) -> {
                    plain.append('$')
                    i += 2
                }

                // 3a. \( ... \) inline maths.
                text.startsWith("\\(", i) -> {
                    val close = text.indexOf("\\)", i + 2)
                    if (close == -1) {
                        plain.append("\\(")
                        i += 2
                    } else {
                        flushPlain()
                        out.add(Segment.InlineMath(text.substring(i + 2, close)))
                        i = close + 2
                    }
                }

                // 3b. \[ ... \] block maths.
                text.startsWith("\\[", i) -> {
                    val close = text.indexOf("\\]", i + 2)
                    if (close == -1) {
                        plain.append("\\[")
                        i += 2
                    } else {
                        flushPlain()
                        out.add(Segment.BlockMath(text.substring(i + 2, close)))
                        i = close + 2
                    }
                }

                // 4. $$ ... $$ block maths, boundary only.
                text.startsWith("$$", i) && isBlockBoundary(text, i, plain.isEmpty()) -> {
                    val close = text.indexOf("$$", i + 2)
                    if (close == -1) {
                        // No closer anywhere: remainder is plain, scan ends.
                        plain.append(text.substring(i))
                        i = n
                    } else if (close == i + 2) {
                        // Empty $$$$ is not maths; keep it visible as plain.
                        plain.append("$$")
                        i += 2
                    } else {
                        flushPlain()
                        out.add(Segment.BlockMath(text.substring(i + 2, close)))
                        i = close + 2
                    }
                }

                // 5. $ ... $ inline maths.
                text[i] == '$' -> {
                    var j = i + 1
                    var close = -1
                    var aborted = false
                    while (j < n) {
                        if (text.startsWith("\\$", j)) {
                            j += 2
                            continue
                        }
                        if (text.startsWith("$$", j)) {
                            // Inside inline maths, $$ opens a block only at a
                            // boundary (preceded by whitespace). Abort this
                            // inline maths and let the outer loop re-handle
                            // the $$ as a block opener. Otherwise the first
                            // '$' closes this inline maths.
                            if (j > i + 1 && text[j - 1].isWhitespace()) {
                                aborted = true
                            } else {
                                close = j
                            }
                            break
                        }
                        if (text[j] == '$') {
                            close = j
                            break
                        }
                        if (text.startsWith("\n\n", j)) {
                            aborted = true
                            break
                        }
                        j++
                    }
                    when {
                        close != -1 && close > i + 1 -> {
                            flushPlain()
                            out.add(Segment.InlineMath(text.substring(i + 1, close)))
                            i = close + 1
                        }
                        close != -1 -> {
                            // Empty $...$ (immediate close) is not maths.
                            plain.append('$')
                            i += 1
                        }
                        aborted -> {
                            // Degrade only the opening '$'; rescan from next char.
                            plain.append('$')
                            i += 1
                        }
                        else -> {
                            // No closing '$' in the remainder: rest is plain.
                            plain.append(text.substring(i))
                            i = n
                        }
                    }
                }

                else -> {
                    plain.append(text[i])
                    i += 1
                }
            }
        }
        flushPlain()
        return out
    }

    /**
     * True when a `$$` at [i] sits at a boundary: start of input, an
     * empty Plain buffer (immediately after a previous segment), after
     * whitespace, or after opening punctuation.
     */
    private fun isBlockBoundary(text: String, i: Int, plainIsEmpty: Boolean): Boolean {
        if (i == 0 || plainIsEmpty) return true
        val prev = text[i - 1]
        return prev.isWhitespace() || prev == '(' || prev == '[' || prev == '{'
    }
}
