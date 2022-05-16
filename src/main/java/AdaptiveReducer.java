import com.google.common.collect.TreeBasedTable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AdaptiveReducer implements Reducer{
    Instance ist;
    List<Integer> sol;
    double epdist;

    public AdaptiveReducer(Instance ist, List<Integer> sol, double epdist) {
        this.ist = ist;
        this.sol = new ArrayList<>(sol);
        this.epdist = epdist;
    }

    @Override
    public List<Integer> toRemove(double deltaT, double deltaD) {
        List<Integer> toRemove = new ArrayList<>();
        //Reduce against time bound using efficiency criterion
        while (deltaT > 0){
            int victim = minEff();
            sol.remove((Integer) victim);
            toRemove.add(victim);
            deltaT -= ist.costs[victim];
        }
        double currentLen = Utils.subtourDistance(sol, ist);
        // redo a routing

        while (currentLen > epdist){

        }
        return toRemove;
    }

    private int minEff(){
        List<Element> order = new ArrayList<>();
        for (int i : sol) {
            order.add(new Element(i, ist.interest[i]/ist.costs[i]));
        }
        order.sort(Comparator.comparing(Element::getValue));
        return order.get(0).getIndex();
    }
}
