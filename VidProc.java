import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * <pre>
 * This script reads video files from current folder, stabilizes and joins them (using ffmpeg) 
 * into a single MP4 (with H.264/AAC encoding) video file (named out.mp4 by default).
 * 
 * Usage -
 * cd to the folder containing the input video files  and run -
 * java VidProc.java [outputVideoFilename]
 * 
 * Depending on the input file sizes and your machine specs, the stabilize and join process might take a long time. In case
 * this process is halted (or stops) midway then on re-running the script, it will try to restart from the video that was
 * incomplete on the last run (so long as the sub-folder named 'vidproc' or any of its files hasn't been deleted).
 * 
 * Once a stabilized video file has been successfully created from the input video files, the subfolder named 'vidproc'
 * may be safely deleted.
 * 
 * Requirements -
 * 1. Java 11+
 * 2. ffmpeg
 * 3. Ensure that all input video files have the same resolution (e.g. 1920x1080).
 * 4. The input files must be named in alphabetical order in the sequence they need to be joined (e.g. 1.mp4, 2.mp4, etc).
 * 5. Output filename (if specified) must be an mp4 file.
 * 
 * </pre>
 * 
 * @author Faram
 * @since 2025-02-09
 */
public class VidProc {

    static final String DEFAULT_OUTPUT_VIDEO_FILENAME = "out.mp4";
    static final String WORK_FOLDER = "vidproc";
    static final String TRANSFORMS_FILE_SUFFIX = ".trf";
    static final String VIDEO_FILE_SUFFIX = ".mp4";
    static final String LISTING_FILENAME = "files.txt";

    // ffmpeg uses this filename by default. Don't change it.
    static final String TEMP_TRANSFORMS_FILE = "transforms.trf";
    static final String TEMP_STABILIZED_VIDEO_FILE = "temp_stabilized.mp4";

    static final Logger logger = Logger.getLogger(VidProc.class.getName());

    // Currently running ffmpeg process invoked by this script.
    static Process ffmpegProcess;

    static List<String> timingStats = new ArrayList<>();

    /**
     * Create a stabilized video file from video files in current folder.
     * 
     * @param args
     * @throws IOException
     * @throws InterruptedException
     */
    public static void main(String[] args) {
        int exitCode = 0;

        try {
            long tick = System.currentTimeMillis();

            // Cleanup temp files
            cleanup();

            // If output file exists then nothing needs to be done
            // Use output video filename provided as a commandline arg, else use default
            // filename
            Path outputVideoFile = Paths.get(args.length > 0 ? args[0] : DEFAULT_OUTPUT_VIDEO_FILENAME);

            String outputFilenameSuffix = outputVideoFile.getFileName().toString()
                    .substring(outputVideoFile.getFileName().toString().lastIndexOf("."));
            if (!outputFilenameSuffix.equalsIgnoreCase(VIDEO_FILE_SUFFIX)) {
                logger.info("Output video filename suffix should be " + VIDEO_FILE_SUFFIX);
                return;
            }

            if (Files.exists(outputVideoFile)) {
                logger.info(outputVideoFile.toAbsolutePath().toString() + " already exists. Nothing to do");
                return;
            }

            // Get original video files in current folder
            List<Path> origVideos = listFilesInFolder(Paths.get("."));

            // Create folder to contain stabilized videos and metadata files
            Path workFolder = createFolder(WORK_FOLDER);

            List<Path> stabilizedVideos = new ArrayList<>();
            origVideos.forEach(
                    origVideo -> stabilizedVideos.add(createStabilizedVideo(workFolder, origVideo)));

            joinStabilizedVideos(stabilizedVideos, outputVideoFile);

            timingStats.stream().forEach(msg -> System.out.println(msg));

            long tock = System.currentTimeMillis();
            logger.info("Stabilized and joined videos in " + (tock - tick) / 1000 + " seconds into "
                    + outputVideoFile.toAbsolutePath().toString());
        } catch (Exception ex) {
            ex.printStackTrace();

            // On abnormal exit, terminate ffmpeg process, if any, invoked by this script.
            if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
                ffmpegProcess.destroy();
            }

            exitCode = 1;
        } finally {
            cleanup();
            System.exit(exitCode);
        }

    }

    /**
     * Cleanup temporary files.
     */
    static void cleanup() {
        try {
            deleteFile(Paths.get(LISTING_FILENAME));
            deleteFile(Paths.get(TEMP_TRANSFORMS_FILE));
            deleteFile(Paths.get(WORK_FOLDER, TEMP_STABILIZED_VIDEO_FILE));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Moves given src file to dest.
     * 
     * @param src
     * @param dest
     * @return
     * @throws IOException
     */
    static void moveFile(Path src, Path dest) throws IOException {
        Files.move(src, dest);
        logger.info("Moved " + src.toAbsolutePath().toString() + " to " + dest.toAbsolutePath().toString());
    }

    /**
     * Copies given src file to dest.
     * 
     * @param src
     * @param dest
     * @return
     * @throws IOException
     */
    static void copyFile(Path src, Path dest) throws IOException {
        Files.copy(src, dest);
        logger.info("Copied " + src.toAbsolutePath().toString() + " to " + dest.toAbsolutePath().toString());
    }

    /**
     * Deletes given file.
     * 
     * @param file
     * @throws IOException
     */
    static void deleteFile(Path file) throws IOException {
        boolean isDeleted = Files.deleteIfExists(file);
        if (isDeleted) {
            logger.info("Deleted " + file.toAbsolutePath().toString());
        }
    }

    /**
     * Get files list (sorted alphabetically) from current folder.
     * 
     * @return
     * @throws IOException
     */
    static List<Path> listFilesInFolder(Path folder) throws IOException {

        List<Path> files = Files.list(folder).filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        files.forEach(f -> logger.info(f.toAbsolutePath().toString()));
        return files;
    }

    /**
     * Creates folder.
     * 
     * @return
     * @throws IOException
     */
    static Path createFolder(String foldername) throws IOException {
        Path folder = Paths.get(foldername);

        if (Files.exists(folder) && Files.isDirectory(folder)) {
            logger.info("Folder already exists: " + folder.toAbsolutePath().toString());
            return folder;
        }

        Files.createDirectories(folder);
        logger.info("Created folder: " + folder.toAbsolutePath().toString());
        return folder;
    }

    /**
     * Creates a stabilized video for given video.
     * 
     * @param workFolder
     * @param origVideo
     * @return stabilized video file.
     * @throws IOException
     * @throws InterruptedException
     */
    static Path createStabilizedVideo(Path workFolder, Path origVideo) {

        try {
            long tick = System.currentTimeMillis();

            // Get original video filename without the extension
            int idx = origVideo.getFileName().toString().lastIndexOf('.');
            String filenameWithoutSuffix = origVideo.getFileName().toString().substring(0, idx);

            Path stabilizedVideo = workFolder.resolve(filenameWithoutSuffix + VIDEO_FILE_SUFFIX);

            // Do nothing if stabilized video was previously successfully created.
            if (Files.exists(stabilizedVideo)) {
                logger.info("Stabilized video already exists: " + stabilizedVideo.toAbsolutePath().toString());
                return stabilizedVideo;
            }

            // Don't analyse video if a transforms file (from a previous run) exists.

            // ffmpeg will write/read transforms to/from this file
            Path tempTransformsFile = Paths.get(TEMP_TRANSFORMS_FILE);
            deleteFile(tempTransformsFile);

            // Once a transform process is done, default transforms file is copied to this
            // file for safekeeping
            Path transformsFile = workFolder.resolve(filenameWithoutSuffix + TRANSFORMS_FILE_SUFFIX);
            long tock = System.currentTimeMillis();

            if (Files.exists(transformsFile)) {
                logger.info("Transforms file already exists: " + transformsFile.toAbsolutePath().toString());
                copyFile(transformsFile, tempTransformsFile);
            } else {
                // Analyse the video and write required transforms to default transforms file.
                logger.info("Creating transforms file for: " + origVideo.toAbsolutePath().toString());
                runCommand(
                        new String[] { "ffmpeg", "-i", origVideo.getFileName().toString(), "-vf", "vidstabdetect", "-f",
                                "null", "-" });

                copyFile(tempTransformsFile, transformsFile);
                tock = System.currentTimeMillis();
                String msg = "Analysed " + stabilizedVideo.toAbsolutePath().toString() + " in "
                        + (tock - tick) / 1000
                        + " seconds and added transforms to " + transformsFile.toAbsolutePath().toString();
                logger.info(msg);
                timingStats.add(msg);
            }

            logger.info("Creating stabilized video for: " + origVideo.toAbsolutePath().toString());
            Path tempStabilizedVideo = workFolder.resolve(TEMP_STABILIZED_VIDEO_FILE);
            deleteFile(tempStabilizedVideo);

            // Stabilize the video. Creates an MP4 file (with default H.264/AAC codecs)
            runCommand(new String[] { "ffmpeg", "-i", origVideo.getFileName().toString(), "-vf",
                    "vidstabtransform", tempStabilizedVideo.toAbsolutePath().toString() });

            moveFile(tempStabilizedVideo, stabilizedVideo);
            deleteFile(tempTransformsFile);

            long tock2 = System.currentTimeMillis();
            String msg = "Stabilized " + stabilizedVideo.toAbsolutePath().toString() + " in " + (tock2 - tock) / 1000
                    + " seconds";
            logger.info(msg);
            timingStats.add(msg);

            return stabilizedVideo;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Join given stabilized videos, without re-encoding, into a single video file.
     * 
     * @param stabilizedVideos
     * @param outputVideoFile
     * @throws IOException
     * @throws InterruptedException
     */
    static void joinStabilizedVideos(List<Path> stabilizedVideos, Path outputVideoFile)
            throws IOException, InterruptedException {

        long tick = System.currentTimeMillis();
        logger.info("Joining " + stabilizedVideos.size() + " video files without re-encoding, into "
                + outputVideoFile.toAbsolutePath().toString());

        Path listingFile = Paths.get(LISTING_FILENAME);

        // Delete listing file if it exists.
        deleteFile(listingFile);

        // Write stabilized videos list to listing file
        try (PrintWriter pw = new PrintWriter(new FileWriter(listingFile.toFile(), true))) {
            stabilizedVideos.stream().forEach(
                    file -> pw
                            .println("file '" + WORK_FOLDER + File.separator + file.getFileName() + "'"));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        // Join the files without re-encoding
        runCommand(new String[] { "ffmpeg", "-f", "concat", "-safe", "0", "-i", LISTING_FILENAME, "-c", "copy",
                outputVideoFile.getFileName().toString() });

        String msg = "Joined stabilized video files into " + outputVideoFile.toAbsolutePath().toString() + " in "
                + (System.currentTimeMillis() - tick) / 1000 + " seconds";
        logger.info(msg);
        timingStats.add(msg);
    }

    /**
     * Run given command, reading out the process's stdout/stderr.
     * 
     * @param cmd
     * @throws IOException
     * @throws InterruptedException
     */
    static void runCommand(String[] cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process proc = pb.start();
        logger.info("Started: " + pb.command().toString());
        int exitCode = proc.waitFor();
        logger.info(pb.command().toString() + " process exited with code: " + exitCode);
    }
}