import specification.Borrowed;

public class PipedOutputStreamWriteAfterCloseError {
    public static class PipedOutputStream {
        // @Ghost
        boolean isConnected;
        // @Ghost
        boolean isClosed;

        // @StateRefinement(to = this.isConnected == false && this.isClosed == false)
        public PipedOutputStream PipedOutputStream() {
            return new PipedOutputStream();
        }

        /*
        @StateRefinement(
            from  = this.isConnected == false && this.isClosed == false
                && sink.isConnected == false && sink.isClosed == false,
            to = this.isConnected == true && sink.isConnected == true
        )
        */
        void connect(@Borrowed PipedInputStream sink) { }

        /*
        @StateRefinement(
            from = this.isConnected == true && this.isClosed == false,
            to = this.isConnected == true && this.isClosed == false
        )
        */
        void write(byte[] b) { }

        /*
        @StateRefinement(
            from = this.isClosed == false,
            to = this.isClosed == true && this.isConnected == false
        )
        */
        void close() { }
    }

    public static class PipedInputStream {
        // @Ghost
        boolean isConnected;
        // @Ghost
        boolean isClosed;

        // @StateRefinement(to = this.isConnected == false && this.isClosed == false)
        public PipedInputStream PipedInputStream() {
            return new PipedInputStream();
        }

        /*
        @StateRefinement(
            from = this.isConnected == false && this.isClosed == false
                && source.isConnected == false && source.isClosed == false,
            to = this.isConnected == true && source.isConnected == true
        )
        */
        void connect(@Borrowed PipedOutputStream source) { }

        /*
        @StateRefinement(
            from = this.isConnected == true && this.isClosed == false,
            to = this.isConnected == true && this.isClosed == false
        )
        */
        int read() { 
            return 1;
        }

        /*
        @StateRefinement(
            from = this.isClosed == false,
            to = this.isClosed == true && this.isConnected == false
        )
        */
        void close() { }
    }

    public static void main(String[] args) {
        PipedOutputStream src = new PipedOutputStream();
        PipedInputStream  snk = new PipedInputStream();
        src.connect(snk);
        src.close(); // close the stream, but then try to write to it
        src.write("Hello".getBytes()); // fails
    }
}
