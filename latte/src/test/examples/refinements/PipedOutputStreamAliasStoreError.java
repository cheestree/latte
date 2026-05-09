import specification.Borrowed;
import specification.Unique;

public class PipedOutputStreamAliasStoreError {
    public static class PipedOutputStream {
        // @Ghost
        boolean isConnected;
        // @Ghost
        boolean isClosed;

        @Unique
        PipedInputStream storedSink;

        public PipedOutputStream() {

        }

        /*
        @StateRefinement(
            from  = this.isConnected == false && this.isClosed == false
                && sink.isConnected == false && sink.isClosed == false,
            to = this.isConnected == true
                && sink.isConnected == true
        )
        */
        void connect(@Borrowed PipedInputStream sink) {
            this.storedSink = sink; // aliasing violation, should only modify the state of this object, not store the reference
        }

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

        @Unique
        PipedOutputStream storedSource;

        // @StateRefinement(to = this.isConnected == false && this.isClosed == false)
        public PipedInputStream() {

        }

        /*
        @StateRefinement(
            from  = this.isConnected == false && this.isClosed == false
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
        src.connect(snk); // snk is now aliased in src, violates the state refinement of connect, which should only modify the state of src
    }
}

