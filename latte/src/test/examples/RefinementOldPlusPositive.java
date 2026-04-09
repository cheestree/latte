package latte;

import specification.Free;
import specification.Unique;

// @Ghost("int score")
public class RefinementOldPlusPositive {

    @Unique MetricCell head;

    public RefinementOldPlusPositive(@Free MetricCell head) {
        this.head = head;
    }

    // @StateRefinement(from="inc > 0", to="score(this) > score(old(this)) + inc - 1")
    void replaceWithRaisedScore(@Free MetricCell fresh, int inc) {
        MetricCell oldHead = this.head;
        this.head = null;
        this.head = fresh;
        oldHead = null;
    }
}

class MetricCell {
    int score;
    @Unique MetricCell next;

    MetricCell(int score, @Free MetricCell next) {
        this.score = score;
        this.next = next;
    }
}
