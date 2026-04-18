package latte;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import specification.Borrowed;
import specification.Free;
import specification.Unique;

public class RefinementPipedOutputStreamPositive {

    @Unique PipedOutputStream src;
    @Unique PipedInputStream snk;

    public RefinementPipedOutputStreamPositive(@Free PipedOutputStream src, @Free PipedInputStream snk) {
        this.src = src;
        this.snk = snk;
    }

    void connectFromSourceThenTransfer() throws IOException {
        PipedOutputStream out = this.src;
        PipedInputStream in = this.snk;
        out.connect(in);
        out.write(65);
        in.read();
    }

    void connectFromSinkThenTransfer() throws IOException {
        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream in = new PipedInputStream();
        in.connect(out);
        out.write(66);
        in.read();
    }

    public static void main(String[] args) throws IOException {
        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream in = new PipedInputStream();
        RefinementPipedOutputStreamPositive test = new RefinementPipedOutputStreamPositive(out, in);
        test.connectFromSourceThenTransfer();
        test.connectFromSinkThenTransfer();
    }
}

// https://docs.oracle.com/javase/7/docs/api/java/io/PipedOutputStream.html
// @ExternalRefinementsFor("java.io.PipedOutputStream")
// @Ghost("int outCount")
// @StateSet({"open", "closed"})
// @StateSet({"disconnected", "connected"})
interface PipedOutputStreamRefinements {

    // @StateRefinement(to="open(this) && disconnected(this) && outCount(this) == 0")
    void PipedOutputStream();

    // @Refinement("snk != null")
    // @StateRefinement(from="open(this) && disconnected(this) && disconnected(snk)", to="open(this) && connected(this) && open(snk) && connected(snk)")
    void connect(@Borrowed PipedInputStream snk) throws IOException;

    // @Refinement("b >= 0 && b <= 255")
    // @StateRefinement(from="open(this) && connected(this)", to="open(this) && connected(this) && outCount(this) == outCount(old(this)) + 1")
    void write(int b) throws IOException;

    // @StateRefinement(from="closed(this)", to="closed(this)")
    // @StateRefinement(from="open(this)", to="closed(this)")
    void close() throws IOException;
}

// https://docs.oracle.com/javase/7/docs/api/java/io/PipedInputStream.html
// @ExternalRefinementsFor("java.io.PipedInputStream")
// @Ghost("int inCount")
// @StateSet({"open", "closed"})
// @StateSet({"disconnected", "connected"})
interface PipedInputStreamRefinements {

    // @StateRefinement(to="open(this) && disconnected(this) && inCount(this) == 0")
    void PipedInputStream();

    // @Refinement("src != null")
    // @StateRefinement(from="open(this) && disconnected(this) && disconnected(src)", to="open(this) && connected(this) && open(src) && connected(src)")
    void connect(@Borrowed PipedOutputStream src) throws IOException;

    // @Refinement("_ >= -1 && _ <= 255")
    // @StateRefinement(from="open(this) && connected(this)", to="open(this) && connected(this) && inCount(this) == inCount(old(this)) + 1")
    int read() throws IOException;

    // @StateRefinement(from="closed(this)", to="closed(this)")
    // @StateRefinement(from="open(this)", to="closed(this)")
    void close() throws IOException;
}
