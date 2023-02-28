(defunc printint [i] <<C
    printf("%d\n", i->value.i);
    return i;
C
)

(defun factorial [n]
    (if (= n 0)
        1
        (* n (factorial (- n 1))) ) )


(printint (factorial 5))
(printint (factorial 6))