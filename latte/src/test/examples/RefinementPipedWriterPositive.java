package latte;

import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;

import specification.Borrowed;
import specification.Free;
import specification.Unique;

public class RefinementPipedWriterPositive {

    @Unique PipedWriter writer;
    @Unique PipedReader reader;

    public RefinementPipedWriterPositive(@Free PipedWriter writer, @Free PipedReader reader) {
        this.writer = writer;
        this.reader = reader;
    }

    void connectThenWrite() throws IOException {
        PipedWriter w = this.writer;
        PipedReader r = this.reader;
        w.connect(r);
        w.write(65);
        w.close();
    }

    public static void main(String[] args) throws IOException {
        PipedWriter writer = new PipedWriter();
        PipedReader reader = new PipedReader();
        RefinementPipedWriterPositive test = new RefinementPipedWriterPositive(writer, reader);
        test.connectThenWrite();
    }
}

// https://docs.oracle.com/javase/7/docs/api/java/io/PipedWriter.html
// @ExternalRefinementsFor("java.io.PipedWriter")
// @Ghost("int writes")
// @StateSet({"open", "closed"})
// @StateSet({"disconnected", "connected"})
// @StateSet({"nothingWritten", "alreadyWritten"})
interface PipedWriterRefinements {

    // @StateRefinement(to="open(this) && disconnected(this) && nothingWritten(this) && writes(this) == 0")
    void PipedWriter();

    // @Refinement("snk != null")
    // @StateRefinement(from="open(this) && disconnected(this) && idle(snk)", to="open(this) && connected(this) && bound(snk)")
    void connect(@Borrowed PipedReader snk) throws IOException;

    // @Refinement("c >= 0 && c <= 65535")
    // @StateRefinement(from="open(this) && connected(this)", to="open(this) && connected(this) && alreadyWritten(this) && writes(this) == writes(old(this)) + 1")
    void write(int c) throws IOException;

    // @StateRefinement(from="closed(this)", to="closed(this)")
    // @StateRefinement(from="open(this)", to="closed(this)")
    void close() throws IOException;
}

// https://docs.oracle.com/javase/7/docs/api/java/io/PipedReader.html
// @ExternalRefinementsFor("java.io.PipedReader")
// @StateSet({"idle", "bound"})
interface PipedReaderRefinements {

    // @StateRefinement(to="idle(this)")
    void PipedReader();
}
