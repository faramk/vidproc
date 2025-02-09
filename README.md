This script reads video files from current folder, stabilizes and joins them (using ffmpeg) 
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