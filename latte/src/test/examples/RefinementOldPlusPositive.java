package latte;

import specification.Free;
import specification.Unique;

// @Ghost("int score")
public class RefinementOldPlusPositive {

    @Unique MetricCellPlus head;

    public RefinementOldPlusPositive(@Free MetricCellPlus head) {
        this.head = head;
    }

    // @StateRefinement(from="inc > 0", to="score(this) > score(old(this)) + inc - 1")
    void replaceWithRaisedScore(@Free MetricCellPlus fresh, int inc) {
        MetricCellPlus oldHead = this.head;
        this.head = null;
        this.head = fresh;
        oldHead = null;
    }

    public static void main(String[] args) {
        MetricCellPlus cell = new MetricCellPlus(10, null);
        RefinementOldPlusPositive test = new RefinementOldPlusPositive(cell);
        MetricCellPlus fresh = new MetricCellPlus(20, null);
        test.replaceWithRaisedScore(fresh, 5);
    }
}

class MetricCellPlus {
    int score;
    @Unique MetricCellPlus next;

    MetricCellPlus(int score, @Free MetricCellPlus next) {
        this.score = score;
        this.next = next;
    }
}
