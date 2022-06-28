import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LKH2Wrapper {
    public static String binPath = "./bin/LKH-3.exe";
    //private static String binPath = "/users/21500078t/LKH-2.0.9/LKH";
    private static Path tempFolder = Paths.get(System.getProperty("java.io.tmpdir"));

    public static void setBinPath(String binPath) {
        LKH2Wrapper.binPath = binPath;
    }

    public static List<Integer> solveRouting(Instance ist, List<Integer> subtour){
        UUID uuid = UUID.randomUUID();
        Path filePath = Paths.get(tempFolder.toString(), "tmp_ist_"+uuid+".tsp");
        Path confPath = Paths.get(tempFolder.toString(), "tmp_conf_"+uuid+".lkh");
        Path outPath = Paths.get(tempFolder.toString(), "tmp_sol_"+uuid+".tour");
        try (PrintWriter pw = new PrintWriter(confPath.toFile());){
            pw.println("PROBLEM_FILE " + filePath.toString());
            pw.println("OUTPUT_TOUR_FILE " + outPath.toString());
        }catch (IOException e){
            e.printStackTrace();
        }

        boolean[] mask = new boolean[ist.size];
        for (int i = 0; i < ist.size; i++) {
            if (subtour.contains(i))
                mask[i] = true;
        }
        Instance toWrite = Instance.fromFilter(ist, mask);
        writeTSPLIB(toWrite, filePath);

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(binPath, confPath.toString());
        try {
            Process process = processBuilder.start();
            //Press any key to continue
            //System.out.print("  Called LKH ...");

            final BufferedReader wr = new BufferedReader(new InputStreamReader(process.getInputStream()));
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            String line = ""; boolean tryExit = false;
            try {
                while ((line = wr.readLine()) != null) {
                    //System.out.println(" " + line);
                    if (line.contains("Time.total")) {
                        tryExit = true;
                    }
                    if (tryExit) {
                        writer.write('c');
                        writer.flush();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0)
                System.out.println("  LKH-2 exited with code : " + exitCode);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            List<String> lines = Files.readAllLines(outPath);
            List<Integer> out = new ArrayList<>();
            boolean reading = false;
            for (String line : lines){
                if (line.contains("EOF"))
                    reading = false;
                if (reading){
                    int node = Integer.parseInt(line.strip());
                    if (node < 0)
                        reading = false;
                    else {
                        out.add(node-1);
                    }
                }
                if (line.contains("TOUR_SECTION"))
                    reading = true;
            }
            return out;
        } catch (IOException e) {
            e.printStackTrace();
            return subtour;
        } finally {
            boolean cleanup = filePath.toFile().delete() && confPath.toFile().delete() && outPath.toFile().delete();
            if (!cleanup)
                System.err.println("[Warning] Couldn't delete temp files for the LKH solver");
        }
    }

    private static void writeTSPLIB(Instance ist, Path filePath){
        try (PrintWriter pw = new PrintWriter(filePath.toFile());){
            pw.println("NAME: tap_tsp_routing");
            pw.println("TYPE: TSP");
            pw.println("COMMENT: autogenerated by LKH2Wrapper");
            pw.println("DIMENSION: " + ist.size);
            pw.println("EDGE_WEIGHT_TYPE: EXPLICIT");
            pw.println("EDGE_WEIGHT_FORMAT: FULL_MATRIX");
            pw.println("EDGE_WEIGHT_SECTION");
            for (int i = 0; i < ist.size; i++) {
                for (int j = 0; j < ist.size; j++) {
                    pw.print((int) ist.distances[i][j]);//TODO handle discretization if real
                    if (j < ist.size -1)
                        pw.print(" ");
                }
                pw.println("");
            }
            pw.println("EOF");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        final String file = "special_200.dat";
        final String path="C:\\Users\\chanson\\Desktop\\instances\\tap_" + file;
        Instance ist = Instance.readFile(path);
        List<Integer> subtour = IntStream.rangeClosed(10, 90).boxed().toList();
        List<Integer> solution = solveRouting(ist, subtour);
        System.out.println(solution);

    }

}
