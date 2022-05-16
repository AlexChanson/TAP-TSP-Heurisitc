import java.util.List;

public interface Reducer {
    List<Integer> reduce(double deltaT, double deltaD);
}
