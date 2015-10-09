package gov.uspto.bdss.util;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FileUtils;

/**
 * Command line application for splitting large, concatenated "XML" files into the separate XML files that comprise it.
 */
public class XmlSplitter {

    private static final Logger log = Logger.getLogger(XmlSplitter.class.getCanonicalName());

    public static final class Options {

        @Parameter(names = "--in", description = "The input \"XML\" file to split. Can be a zip file containing a single \"XML\", as well")
        String in;

        @Parameter(names = "--out", description = "Directory where the output XML files should be stored")
        String out = "splitxml";

        @Parameter(names = "--encoding", description = "The encoding of the XML file")
        String encoding = "UTF-8";

        @Parameter(names = "--template", description = "Format pattern for how the output XML files should be named")
        String template = "segment-%1$04d.xml";

        @Parameter(names = "--excludeprolog", description = "Set to exclude the prolog in the output XML files")
        boolean excludeProlog = false;

        @Parameter(names = "--debug", description = "Set to enable debugging")
        boolean debug = false;

        @Parameter(names = "--zipped", description = "Forces recognition of the input as zipped, even when the filename does not end in .zip")
        boolean zipped = false;

        @Parameter(names = "--haltonerror", description = "When set, the entire split operation will stop if a single segment cannot be processed")
        boolean haltOnFailure = false;

        @Parameter(names = "--help", help = true, description = "Shows the help")
        private boolean help;

        InputStream inStream() throws IOException {
            if (in == null) {
                return System.in;
            }

            InputStream inStream = null;
            try {
                inStream = new FileInputStream(in);
            } catch (FileNotFoundException ex) {
                throw new IllegalArgumentException("Cannot find file " + in, ex);
            }

            if (zipped || in.endsWith(".zip")) {
                ZipInputStream zin = new ZipInputStream(inStream);
                zin.getNextEntry();
                return zin;
            } else {
                return inStream;
            }
        }

        File outDir() {
            File outFile = new File(out);
            if (!outFile.exists()) {
                outFile.mkdirs();
            } else if (!outFile.isDirectory()) {
                throw new IllegalArgumentException(out + " is not a directory.");
            }
            return outFile;
        }

        String encoding() {
            try {
                Charset.forName(encoding);
            } catch (UnsupportedCharsetException ex) {
                throw new IllegalArgumentException(encoding + " is not a supported charset.", ex);
            }
            return encoding;
        }

    }

    public static void main(String[] args) throws IOException {
        Options opts = new Options();
        JCommander commander = new JCommander(opts, args);
        if (opts.debug) {
            log.getParent().setLevel(Level.FINE);
        }
        if (opts.help) {
            commander.usage();
            System.exit(0);
        }

        int segment = 0;
        File outDir = opts.outDir();

        long startTime = System.nanoTime();
        try (InputStream allIn = opts.inStream()) {

            Iterable<InputStream> segmentedStreams = new ConcatenatedXmlStream(allIn, opts.encoding(), opts.excludeProlog);
            for (InputStream in : segmentedStreams) {
                segment++;
                File outFile = new File(outDir, String.format(opts.template, segment));
                try {
                    FileUtils.copyInputStreamToFile(in, outFile);
                } catch (IOException ex) {
                    System.err.println("Error processing segment " + segment);
                    ex.printStackTrace(System.err);
                    if (opts.haltOnFailure) {
                        System.exit(1);
                    }
                }
            }

            long endTime = System.nanoTime();
            System.out.println("----------------------------");
            System.out.println("Segments processed    :\t" + segment);
            System.out.println("Total processing time :\t" + ((endTime - startTime) / 1e9) + " seconds");
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            commander.usage();
        } catch (Exception ex) {
            System.err.println("Unable to split input.");
            ex.printStackTrace(System.err);
        }
    }

}
