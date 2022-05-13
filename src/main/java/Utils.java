import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Utils {

    public static double getLB(Instance ist, double eptime, double epdist){
        List<Integer> solution = new ArrayList<>();
        List<Element> order = new ArrayList<>();
        for (int i = 0; i < ist.size; i++) {
            order.add(new Element(i, ist.interest[i]));
        }
        order.sort(Comparator.comparing(Element::getValue).reversed());

        double total_dist = 0;
        double total_time = 0;
        double z = 0;

        for (int i = 0; i < ist.size; i++)
        {
            int current = order.get(i).index;

            if (eptime - (total_time + ist.costs[current]) >= 0){
                if (solution.size() > 0 && epdist - (total_dist + ist.distances[solution.get(solution.size() - 1)][current]) < 0)
                    continue;
                if (solution.size() > 0)
                    total_dist += ist.distances[solution.get(solution.size() - 1)][current];
                total_time += ist.costs[current];
                solution.add(current);
                z += ist.interest[current];
            }
        }
        System.out.println("LB Solution: " + solution);
        return subtourValue(solution, ist);
    }


    public static double subtourValue(List<Integer> tour, Instance ist){
        return tour.stream().mapToDouble(i -> ist.interest[i]).sum();
    }

    public static double subtourTime(List<Integer> tour, Instance ist){
        return tour.stream().mapToDouble(i -> ist.costs[i]).sum();
    }

    public static double sequenceDistance(List<Integer> tour, Instance ist){
        double d = 0;
        for (int i = 0; i < tour.size() - 1; i++) {
            d += ist.distances[tour.get(i)][tour.get(i+1)];
        }
        return d;
    }

    public static double subtourDistance(List<Integer> tour, Instance ist){
        return ist.distances[tour.get(tour.size() - 1)][tour.get(0)] + sequenceDistance(tour, ist);
    }
}
