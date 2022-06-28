import com.google.common.collect.TreeBasedTable;
import edu.princeton.cs.algs4.In;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class AdaptiveReducer implements Reducer{
    Instance ist;
    List<Integer> sol;
    double epdist;
    double eptime;
    Predicate<Integer> triggerTourReopt;

    public AdaptiveReducer(Instance ist, List<Integer> sol, double epdist, double eptime) {
        this.ist = ist;
        this.sol = new ArrayList<>(sol);
        this.epdist = epdist;
        this.eptime = eptime;
        triggerTourReopt = i -> {return true;};
    }

    public AdaptiveReducer(Instance ist, List<Integer> sol, double epdist, double eptime, Predicate<Integer> triggerTourReopt) {
        this(ist, sol, epdist, eptime);
        this.triggerTourReopt = triggerTourReopt;
    }

    @Override
    public List<Integer> reduce(double deltaT, double deltaD) {
        double currentLen = Utils.subtourDistance(sol, ist) - Utils.maxEdgeValue(sol, ist);
        // redo a routing
        List<Integer> routing = LKH2Wrapper.solveRouting(ist, sol);
        if (Utils.subtourDistance(sol, ist) > Utils.subtourDistance(routing, ist)) {
            sol = new ArrayList<>(routing);
            currentLen = Utils.subtourDistance(sol, ist) - Utils.maxEdgeValue(sol, ist);
        }

        int iter = 1;
        while (currentLen > epdist){
            int victim = minInt();
            sol.remove((Integer) victim);
            deltaT -= ist.costs[victim]; // also remove it's cost
            currentLen = Utils.subtourDistance(sol, ist) - Utils.maxEdgeValue(sol, ist);
            if (triggerTourReopt.test(iter)) {
                routing = LKH2Wrapper.solveRouting(ist, sol);
                if (Utils.subtourDistance(sol, ist) - Utils.maxEdgeValue(sol, ist) > Utils.subtourDistance(routing, ist) - Utils.maxEdgeValue(routing, ist)) {
                    sol = new ArrayList<>(routing);
                    currentLen = Utils.subtourDistance(sol, ist) - Utils.maxEdgeValue(sol, ist);
                }
            }
            iter++;
        }

        //Reduce against time bound using efficiency criterion
        while (deltaT > 0){
            int victim = minEff();
            sol.remove((Integer) victim);
            deltaT -= ist.costs[victim];
        }
        return sol;
    }

    private int minEff(){
        List<Element> order = new ArrayList<>();
        for (int i : sol) {
            order.add(new Element(i, ist.interest[i]/ist.costs[i]));
        }
        order.sort(Comparator.comparing(Element::getValue));
        return order.get(0).getIndex();
    }

    private int minInt(){
        List<Element> order = new ArrayList<>();
        for (int i : sol) {
            order.add(new Element(i, ist.interest[i]));
        }
        order.sort(Comparator.comparing(Element::getValue));
        return order.get(0).getIndex();
    }
}
