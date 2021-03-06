import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import edu.princeton.cs.algs4.AssignmentProblem;
import lombok.Getter;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.SpanningTreeAlgorithm;
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree;
import org.jgrapht.alg.util.Pair;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class TSPStyle {
    @Parameter(names={"--instances", "-i"})
    String ist_folder;
    @Parameter(names={"--res", "-r"})
    String res_file;
    @Parameter(names = {"--debug", "-d"})
    boolean debug;
    @Parameter(names = {"--lkh"})
    String lkhPath = null;
    @Parameter(names = {"-t", "--threads"})
    int threads = 2;

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        //REMOVE ME
        //double temps = 0.6, dist = 0.3;
        //Instance ist = Instance.readFile("C:\\Users\\chanson\\Desktop\\tap_special_10.dat");
        //double eptime = Math.round(temps * ist.size * 27.5); //f1
        //double epdist = Math.round(dist * ist.size * 5.5); //f1
        //List<Integer> sol = solve(ist, eptime, epdist, true);
        //System.out.println(Utils.subtourValue(sol, ist));
        //System.out.println(sol);
        //System.exit(0);

        TSPStyle main = new TSPStyle();
        JCommander.newBuilder()
                .addObject(main)
                .build()
                .parse(args);
        main.run();
    }

    public void run() throws IOException, ExecutionException, InterruptedException {
        if (lkhPath != null)
            LKH2Wrapper.binPath = lkhPath;

        double temps = 0.6, dist = 0.3;

        final PrintWriter out = new PrintWriter(new File(res_file));
        out.println("series_id;size;time;z;solution");

        List<Pair<Integer, Integer>> configs = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            for (int size : new int[]{40, 60, 80, 100, 200, 300}) {
                configs.add(new Pair<>(i, size));
            }
        }
        ForkJoinPool pool = new ForkJoinPool(threads);
        pool.submit(() -> configs.parallelStream().forEach( conf -> {

                int series_id = conf.getFirst(), size = conf.getSecond();
                final String path = ist_folder + "tap_" + series_id + "_" + size + ".dat";
                Instance ist = Instance.readFile(path);
                System.out.println("Loaded " + path + " | " + ist.size + " queries");

                double epdist = 0, eptime =0;
                if (ist_folder.contains("_f3") || ist_folder.contains("_f4")) {
                    epdist = Math.round(dist * size * 4.5);
                    eptime = Math.round(temps * size * 27.5f);
                } else if (ist_folder.contains("_f1")) {
                    eptime = Math.round(temps * size * 27.5); //f1
                    epdist = Math.round(dist * size * 5.5); //f1
                } else if (ist_folder.contains("_f2")){
                    epdist = Math.round(dist * size * 7); //f2
                    eptime = Math.round(temps * size * 6); //f2
                } else {
                    System.err.println("Instances unknown !!!");
                    epdist = Math.round(dist * size * 4.5);
                    eptime = Math.round(temps * size * 27.5f);
                }

                long startTime = System.nanoTime();
                List<Integer> solution = solve(ist, eptime, epdist, debug);
                long endTime = System.nanoTime();
                long duration = (endTime - startTime) / 1000000;

                try {
                    FileOutputStream fos = new FileOutputStream(ist_folder + "tap_" + series_id + "_" + size + ".tsp");
                    PrintWriter pw = new PrintWriter(fos);
                    pw.println(solution.toString().replace("[", "").replace("]", "").replace(", ", " "));
                    pw.close();
                    fos.close();
                } catch (IOException e){
                    e.printStackTrace();
                }

                //System.out.println("$RES$=" + series_id + "," + ist.size + "," + duration / 1000.0 + ";" + Utils.subtourValue(full, ist) + ";" + full.toString().replace("[", "").replace("]", ""));
                out.println(series_id + ";" + size + ";" + duration / 1000.0 + ";" + Utils.subtourValue(solution, ist) + ";" + solution.toString().replace("[", "").replace("]", ""));
                out.flush();

        })).get();

        out.close();
    }

    public static List<Integer> solve(Instance ist, double eptime, double epdist, boolean debug){
        var lbsol = Utils.getLB(ist, eptime, epdist);
        List<Integer> solution =  lbsol.solution;
        double lb = lbsol.lb;
        System.out.println("  LB = " + lb);

        // 1 solve affectation
        List<List<Integer>> subtours = solveAffectation(ist.distances);
        System.out.println("Subtours");
        System.out.println("  |S| = " + subtours.size() + ", sum |s| in S = " + subtours.stream().mapToInt(List::size).sum());
        System.out.println("  " + subtours.stream().map(List::size).collect(Collectors.toList()));

        List<List<Integer>> selected = new ArrayList<>();

        // Path A vs Path B we can stitch everything or only a subset of tours

        // 2.1.1 solve Md-KS to find a collection of subtours
        boolean[] KSSolution = MDKnapsack.solve2DNaive(
                subtours.stream().mapToDouble(st -> Utils.subtourValue(st, ist)).toArray(),
                subtours.stream().mapToDouble(st -> Utils.subtourTime(st, ist)).toArray(), eptime,
                subtours.stream().mapToDouble(st -> Utils.subtourDistance(st, ist)-Utils.maxEdgeValue(st, ist)).toArray(), epdist);
        for (int i = 0; i < KSSolution.length; i++) {
            if (KSSolution[i])
                selected.add(subtours.get(i));
        }

        double path_b_ub = selected.stream().flatMap(List::stream).mapToDouble(idx -> ist.interest[idx]).sum();
        if (lb > path_b_ub) {
            selected.clear();
            selected.addAll(subtours);
            System.out.println("Selected path A over lb condition");
        } else
            System.out.println("Selected path B");

        // 2.1.2 stitch subtours
        List<Integer> full;
        if (selected.size() > 1)
            full = stitch(selected, ist);
        else
            full = selected.get(0);
        System.out.println("Checking subtour stiching ... " + full.size() + "/" + selected.stream().mapToInt(List::size).sum());

        // 2.1.3 check constraint (distance)
        if (debug) {
            System.out.println("Objective: " + Utils.subtourValue(full, ist));
            System.out.println("Time constraint: " + Utils.subtourTime(full, ist) + "/" + eptime);
            System.out.println("Distance constraint: " + (Utils.subtourDistance(full, ist) - Utils.maxEdgeValue(full, ist)) + "/" + epdist);
        }
        boolean cstr_check = Utils.subtourDistance(full, ist) > epdist + Utils.maxEdgeValue(full, ist) || Utils.subtourTime(full, ist) > eptime;

        // 2.1.4
        if (cstr_check) {
            System.out.println("  Constraint(s) violated running reducer");

            //Switch to sequence for this
            int posme = Utils.argMaxEdge(full, ist);
            if (posme != 0)
                full = getAligned(full, posme);

            Reducer rd = new AdaptiveReducer(ist, full, epdist, eptime);
            full = rd.reduce(Utils.subtourTime(full, ist) - eptime, Utils.sequenceDistance(full, ist) - epdist);

            if (debug) {
                System.out.println("  Objective: " + Utils.subtourValue(full, ist));
                System.out.println("  Time constraint: " + Utils.subtourTime(full, ist) + "/" + eptime);
                System.out.println("  Distance constraint: " + (Utils.subtourDistance(full, ist) - Utils.maxEdgeValue(full, ist)) + "/" + epdist);
            }
        }

        if (lb <= path_b_ub) {
            System.out.println("Running path A");
            List<Integer> full_alt = stitch(subtours, ist);
            cstr_check = Utils.subtourDistance(full_alt, ist) > epdist + Utils.maxEdgeValue(full_alt, ist) || Utils.subtourTime(full_alt, ist) > eptime;

            if (cstr_check) {
                //Switch to sequence for this
                int posme = Utils.argMaxEdge(full_alt, ist);
                if (posme != 0)
                    full_alt = getAligned(full_alt, posme);

                Reducer rd = new AdaptiveReducer(ist, full_alt, epdist, eptime);
                full_alt = rd.reduce(Utils.subtourTime(full_alt, ist) - eptime, Utils.sequenceDistance(full_alt, ist) - epdist);

                if (debug) {
                    System.out.println("  Objective: " + Utils.subtourValue(full_alt, ist));
                    System.out.println("  Time constraint: " + Utils.subtourTime(full_alt, ist) + "/" + eptime);
                    System.out.println("  Distance constraint: " + (Utils.subtourDistance(full_alt, ist) - Utils.maxEdgeValue(full_alt, ist)) + "/" + epdist);
                }
            }

            if (Utils.subtourValue(full_alt, ist) > Utils.subtourValue(full, ist))
                full = full_alt;
        }

        System.out.println("Val:" + Utils.subtourValue(full, ist));
        if (Utils.subtourValue(full, ist) > Utils.subtourValue(solution, ist))
            solution = full;

        return solution;
    }



    public static List<Integer> stitch(List<List<Integer>> tours, Instance ist){

        Graph<Integer, StitchOP> g = new SimpleGraph<>(StitchOP.class);
        IntStream.rangeClosed(0, tours.size()).forEach(g::addVertex);
        for (int i = 0; i < tours.size(); i++) {
            for (int j = i+1; j < tours.size(); j++) {
                g.addEdge(i, j, getApproximateStitch(tours.get(i), tours.get(j), ist));
            }
        }

        KruskalMinimumSpanningTree<Integer, StitchOP> mst = new KruskalMinimumSpanningTree<>(g);
        SpanningTreeAlgorithm.SpanningTree<StitchOP> tree = mst.getSpanningTree();

        List<Integer> full = new ArrayList<>();
        HashSet<List<Integer>> done = new HashSet<>();
        for (StitchOP op : tree){
            if (full.size() == 0){
                full = execute(op);
                done.add(op.aptr);
                done.add(op.bptr);
                continue;
            }

            if (done.contains(op.aptr) && ! done.contains(op.bptr)){
                full = execute(getApproximateStitch(full, op.bptr, ist));
                done.add(op.bptr);
                continue;
            }

            if (! done.contains(op.aptr) && done.contains(op.bptr)){
                full = execute(getApproximateStitch(full, op.aptr, ist));
                done.add(op.aptr);
                continue;
            }

        }

        return full;
    }

    public static List<Integer> execute(StitchOP op){
        List<Integer> alignedA = getAligned(op.aptr, op.aptr.indexOf(op.vertex1a));
        List<Integer> alignedB = getAligned(op.bptr, op.bptr.indexOf(op.vertex1b));
        alignedA.addAll(alignedB);
        return alignedA;
    }

    private static List<Integer> getAligned(List<Integer> original, int start){
        List<Integer> alignedA = new ArrayList<>();
        int cnt = original.size();
        int ptr = start;
        while (cnt != 0){
            alignedA.add(original.get(ptr));
            cnt--;
            if (ptr == original.size()-1)
                ptr = 0;
            else
                ptr++;
        }
        return alignedA;
    }

    // alternating algorithm from https://vlsicad.ucsd.edu/Publications/Journals/j67.pdf#page=11&zoom=100,0,422
    public static StitchOP getApproximateStitch(List<Integer> a, List<Integer> b, Instance ist){
        HashSet<StitchOP> history = new HashSet<>();
        StitchOP current = new StitchOP();
        current.vertex1a = a.get(0);
        current.vertex2a = a.get(1);
        current.vertex1b = b.get(0);
        current.vertex2b = b.get(1);
        current.aptr = a;
        current.bptr = b;

        for (int iter = 0; !(history.contains(current)); iter++) {
            ArrayList<StitchOP> candidates;
            if (iter % 2 == 0){
                candidates = new ArrayList<>(b.size() * 2);
                for (int i = 0; i < b.size()-1; i++) {
                    StitchOP candidate = new StitchOP(current);
                    candidate.gains = current.cost - (- ist.distances[b.get(i)][b.get(i+1)] - ist.distances[current.vertex1a][current.vertex2a]
                            + ist.distances[b.get(i+1)][current.vertex2a] + ist.distances[b.get(i)][current.vertex1a]);
                    candidate.vertex1b = b.get(i);
                    candidate.vertex2b = b.get(i+1);
                    candidates.add(candidate);
                    candidate = new StitchOP(current);
                    candidate.gains = current.cost - (- ist.distances[b.get(i)][b.get(i+1)] - ist.distances[current.vertex1a][current.vertex2a]
                            + ist.distances[b.get(i)][current.vertex2a] + ist.distances[b.get(i+1)][current.vertex1a]);
                    candidate.vertex2b = b.get(i);
                    candidate.vertex1b = b.get(i+1);
                    candidates.add(candidate);
                }
            } else {
                candidates = new ArrayList<>(a.size() * 2);
                for (int i = 0; i < a.size()-1; i++) {
                    StitchOP candidate = new StitchOP(current);
                    candidate.gains = current.cost - (- ist.distances[current.vertex1b][current.vertex2b] - ist.distances[a.get(i)][a.get(i+1)]
                            + ist.distances[current.vertex2b][a.get(i+1)] + ist.distances[current.vertex1b][a.get(i)]);
                    candidate.vertex1a = a.get(i);
                    candidate.vertex2a = a.get(i+1);
                    candidates.add(candidate);
                    candidate = new StitchOP(current);
                    candidate.gains = current.cost - (- ist.distances[current.vertex1b][current.vertex2b] - ist.distances[a.get(i)][a.get(i+1)]
                            + ist.distances[current.vertex2b][a.get(i)] + ist.distances[current.vertex1b][a.get(i+1)]);
                    candidate.vertex2a = a.get(i);
                    candidate.vertex1a = a.get(i+1);
                    candidates.add(candidate);

                }
            }
            candidates.removeIf(stitchOP -> stitchOP.gains < 0);

            if (candidates.size() == 0) {
                current.cost = - ist.distances[current.vertex1b][current.vertex2b] - ist.distances[current.vertex1a][current.vertex2a]
                        + ist.distances[current.vertex2b][current.vertex2a] + ist.distances[current.vertex1b][current.vertex1a];
                return current;
            }

            candidates.sort(Comparator.comparing(StitchOP::getGains).reversed());
            current = candidates.get(0);
            current.cost = - ist.distances[current.vertex1b][current.vertex2b] - ist.distances[current.vertex1a][current.vertex2a]
                    + ist.distances[current.vertex2b][current.vertex2a] + ist.distances[current.vertex1b][current.vertex1a];


            if (iter > 5)
                history.add(current);
        }

        return current;
    }


    public static List<List<Integer>> solveAffectation(double[][] distances){
        List<List<Integer>> subtours = new ArrayList<>();

        distances = Arrays.stream(distances).map(double[]::clone).toArray(double[][]::new);
        // Make sure distance to self is very high ~= infinite
        for (int i = 0; i < distances.length; i++) {
            distances[i][i] = 10e9;
        }

        //Call hungarian method lib
        AssignmentProblem assignmentProblem = new AssignmentProblem(distances);
        //Remember points already assigned to subtours
        TreeSet<Integer> done = new TreeSet<>();
        //We can stop early if all points are put in their subtours
        for (int i = 0; i < distances.length && done.size() != distances.length; i++) {
            if (!done.contains(i)){
                ArrayList<Integer> subtour = new ArrayList<>();
                int start = i;
                int current = -1;
                subtour.add(i);
                done.add(i);
                // follow along until back at the 'first' point
                current = assignmentProblem.sol(start);
                while (current != start){
                    done.add(current);
                    subtour.add(current);
                    current = assignmentProblem.sol(current);
                }
                subtours.add(subtour);
            }
        }

        //System.out.println(subtours);
        return subtours;
    }

    static class StitchOP  extends DefaultEdge implements Comparable<StitchOP>{
        @Getter
        double cost, gains;
        int vertex1a, vertex1b;
        int vertex2a, vertex2b;
        List<Integer> aptr, bptr;

        public StitchOP() {
            cost = 0;
        }

        public StitchOP(StitchOP old) {
            this.cost = old.cost;
            this.vertex1a = old.vertex1a;
            this.vertex2a = old.vertex2a;
            this.vertex1b = old.vertex1b;
            this.vertex2b = old.vertex2b;
            this.aptr = old.aptr;
            this.bptr = old.bptr;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StitchOP stitchOP = (StitchOP) o;
            return vertex1a == stitchOP.vertex1a && vertex1b == stitchOP.vertex1b && vertex2a == stitchOP.vertex2a && vertex2b == stitchOP.vertex2b;
        }

        @Override
        public int hashCode() {
            return Objects.hash(vertex1a, vertex1b, vertex2a, vertex2b);
        }

        @Override
        public int compareTo(StitchOP o) {
            return Double.compare(this.cost, o.cost);
        }

        @Override
        public String toString() {
            return "StitchOP{" +"cost=" + cost + ", (" + vertex1a + "," + vertex2a + "), (" + vertex1b +"," + vertex2b  + ")}";
        }
    }

}
