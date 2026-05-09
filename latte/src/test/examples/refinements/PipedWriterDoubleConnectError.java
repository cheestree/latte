import specification.Borrowed;

public class PipedWriterDoubleConnectError {

    public static class PipedWriter {
        // @Ghost
        boolean isConnected;

        // @StateRefinement(to = this.isConnected == false)
        public PipedWriter() {

        }

        /*
        @StateRefinement(
            from = this.isConnected == false && reader.isConnected == false,
            to = this.isConnected == true && reader.isConnected == true
        )
        */
        void connect(@Borrowed PipedReader reader) { }

        /*
        @StateRefinement(
            from = this.isConnected == true,
            to = this.isConnected == true
        )
        */
        void write(String s) { }
    }

    public static class PipedReader {
        // @Ghost
        boolean isConnected;

        // @StateRefinement(to = this.isConnected == false)
        public PipedReader() {

        }

        /*
        @StateRefinement(
            from = this.isConnected == false && writer.isConnected == false,
            to = this.isConnected == true && writer.isConnected == true
        )
        */
        void connect(@Borrowed PipedWriter writer) { }

        /*
        @StateRefinement(
            from = this.isConnected == true,
            to = this.isConnected == true
        )
        */
        void read() { }
    }

    public static void main(String[] args) {
        PipedWriter src  = new PipedWriter();
        PipedReader snk1 = new PipedReader();
        PipedReader snk2 = new PipedReader();
        src.connect(snk1);
        src.connect(snk2); // already connected to snk1, error
    }
}