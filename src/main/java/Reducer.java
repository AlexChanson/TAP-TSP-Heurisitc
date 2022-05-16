import java.util.List;

public interface Reducer {
    List<Integer> toRemove(double deltaT, double deltaD);
}
