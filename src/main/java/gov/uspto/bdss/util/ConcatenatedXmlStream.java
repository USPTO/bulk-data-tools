package gov.uspto.bdss.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allows a single InputStream representing many concatenated XML documents to be processed as an iterator of individual
 * InputStreams, each representing one XML file. Each XML file in the concatenated stream starts with an XML prolog. This
 * class looks for these prologs to determine if the iterator should keep providing InputStreams.
 */
public class ConcatenatedXmlStream implements Iterable<InputStream> {

    private static final Logger log = Logger.getLogger(ConcatenatedXmlStream.class.getCanonicalName());

    private final Charset encoding;
    private final InputStream concatenatedStream;
    private final boolean hideSeparators;

    /**
     * @param concatenatedStream - the backing stream
     * @param encodingName - how the character data in the backing stream is encoded.
     */
    public ConcatenatedXmlStream(InputStream concatenatedStream, String encodingName) {
        this(concatenatedStream, encodingName, false);
    }

    /**
     * @param concatenatedStream - the backing stream
     * @param encodingName - how the character data in the backing stream is encoded.
     * @param hideSeparators - if true, separators encountered in the input will not manifest themselves in the segmented
     * InputStreams provided by the iterator.
     */
    public ConcatenatedXmlStream(InputStream concatenatedStream, String encodingName, boolean hideSeparators) {
        this.concatenatedStream = concatenatedStream;
        this.encoding = Charset.forName(encodingName);
        this.hideSeparators = hideSeparators;
    }

    /**
     * Returned iterator does not support the {@code remove} method.
     */
    @Override
    public Iterator<InputStream> iterator() {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(concatenatedStream, encoding));
        return new Iterator<InputStream>() {

            private BufferedXmlSegmentStream current;

            @Override
            public boolean hasNext() {
                return current == null || !current.isLastSegment();
            }

            @Override
            public InputStream next() {
                if (hasNext()) {
                    // on the call to next(), the starting text will be null. Afterwards, the starting text wil be non-null.
                    // nullness is important for preventing the leading separator from creating a segemented stream containing
                    // only the separator
                    String startingText = current == null ? null : current.getNextSegmentStartingText();
                    try {
                        log.fine("Creating new segment stream.");
                        current = new BufferedXmlSegmentStream(reader, encoding, hideSeparators, startingText);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                } else {
                    throw new NoSuchElementException();
                }
                return current;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    private static class BufferedXmlSegmentStream extends InputStream {

        private static final String EOL = System.getProperty("line.separator");
        private static final Pattern SEGMENT_START_PATTERN = Pattern.compile("<\\?xml.*?\\?>", Pattern.CASE_INSENSITIVE);
        private final Segment segment;

        /**
         * Represents the segment entirely in-memory, which presumes each XML segment is sufficiently small enough to be retained
         * in memory.
         */
        static class Segment extends ByteArrayInputStream {

            /**
             * Indicates if this is the last segment.
             */
            final boolean last;

            /**
             * Captures text that was read by a previous segment that actually belongs to this segment. For example, if we are
             * using the XML prolog as our separator, a line of text such as
             * <pre>
             *  </root><?xml version="1.0" encoding="UTF-8"?><root>
             * </pre>
             *
             * Should have this as the value:
             * <pre>
             * <?xml version="1.0" encoding="UTF-8"?><root>
             * </pre>
             */
            final String nextSegmentStartingText;

            public Segment(String xml, Charset charset, boolean last) throws IOException {
                this(xml, charset, last, null);
            }

            public Segment(String xml, Charset charset, boolean last, String nextSegmentStartingText) throws IOException {
                super(xml.getBytes(charset));
                this.last = last;
                this.nextSegmentStartingText = nextSegmentStartingText;
            }

        }

        BufferedXmlSegmentStream(BufferedReader reader, Charset charset, boolean hideSeparators, String startingText) throws IOException {
            this.segment = readSegment(reader, charset, SEGMENT_START_PATTERN, hideSeparators, startingText);
        }

        private Segment readSegment(BufferedReader reader, Charset encoding, Pattern segmentSeparator,
                boolean hideSeparators, String startingText) throws IOException {
            StringBuilder sb = new StringBuilder();
            if (startingText != null) {
                sb.append(startingText);
            }

            boolean honorSeparator = startingText != null; // for ignoring the very first separator
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher match = segmentSeparator.matcher(line);
                boolean foundSeparator = match.find();

                if (foundSeparator) {
                    int startOfMatch = match.start();
                    String beforeMatch = line.substring(0, startOfMatch);
                    String leftover = line.substring(hideSeparators ? match.end() : startOfMatch) + EOL;
                    if (honorSeparator) {
                        return new Segment(sb.append(beforeMatch).toString(), encoding, false, leftover);
                    } else {
                        sb.append(beforeMatch).append(leftover);
                    }
                } else {
                    sb.append(line).append(EOL);
                }

                if (foundSeparator) {
                    honorSeparator = true;
                }
            }
            return new Segment(sb.toString(), encoding, true);
        }

        @Override
        public int read() throws IOException {
            return segment.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return segment.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return segment.read(b, off, len);
        }

        /**
         * Does nothing. It's up to the originator of the backing stream to close the backing stream itself.
         */
        @Override
        public void close() throws IOException {
            // intentionally empty
        }

        @Override
        public int available() throws IOException {
            return segment.available();
        }

        @Override
        public boolean markSupported() {
            return segment.markSupported();
        }

        @Override
        public synchronized void mark(int readlimit) {
            segment.mark(readlimit);
        }

        /**
         * Returns true if this is the last segment of the entire file.
         */
        boolean isLastSegment() {
            return segment.last;
        }

        /**
         * Returns the text that was processed by this segment stream but belongs to the next segment stream.
         */
        String getNextSegmentStartingText() {
            return segment.nextSegmentStartingText;
        }

    }
}
