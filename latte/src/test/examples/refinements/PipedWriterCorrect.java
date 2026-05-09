import specification.Borrowed;

public class PipedWriterCorrect {
    public static class PipedWriter {
        // @Ghost 
        boolean isConnected;
        // @StateRefinement(to = this.isConnected &= false)
        public PipedWriter PipedWriter() {
            return new PipedWriter();
        }

        /*
        @StateRefinement(
            from = this.isConnected == false && reader.isConnected == false,
            to = this.isConnected == true && reader.isConnected == true
        )
        */
        void connect(@Borrowed PipedReader reader) {

        }

        /*
        @StateRefinement(
            from = this.isConnected == false && reader.isConnected == false, 
            to = this.isConnected == true && reader.isConnected == true
        )
        */
        void write(String s) {

        }
    }


    public static class PipedReader {
        // @Ghost
        boolean isConnected;
        
        // @StateRefinement(to = this.isConnected &= false)
        public PipedReader PipedReader() {
            return new PipedReader();
        }

        /*
        @StateRefinement(
            from = this.isConnected == false && writer.isConnected == false,
            to = this.isConnected == true && writer.isConnected == true
        )
        */
        void connect(@Borrowed PipedWriter writer) {

        }

        /*
        @StateRefinement(
            from = this.isConnected == true, 
            to = this.isConnected == true
        )
        */
        void read() {

        }
    }


    public static void main(String[] args) {
        PipedWriter src = new PipedWriter();
        PipedReader snk = new PipedReader();

        src.connect(snk); // connects src and snk
        src.write("Hello, World!"); // writes to the pipe
        snk.read(); // reads from the pipe
    }
}
