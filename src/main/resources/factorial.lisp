(defunc printint [i] <<C
    printf("%d\n", i->value.i);
    return i;
C
)

(defunc printints [i i2] <<C
    printf("%d %d\n", i->value.i, i2->value.i);
    return i;
C
)

(defunc putchar [c] <<C
    putchar(c->value.c);
    return c;
C
)

(defun ps [s] (if (= (size s) 0)
    s
    (do
        (putchar (head s))
        (recur (tail s))
    )
))

(defunc list [] <<C
    return lisp__list_constructor();
C
)

(defun factorial [n]
    (if (= n 0)
        1
        (* n (factorial (- n 1))) ) )

(defun subber [i]
    (fn [j] (- j i)))


(defun for_in_range [start stop acc f]
    (if (= acc stop)
        1
        (do
            (f acc)
            (recur start stop (- acc 1) f)
        )))

(printint (factorial 5))
(printint (factorial 6))
(printint ((subber 1) 10))
(printint ((subber 2) 10))

(for_in_range 11 5 11 (fn [i] (printints i (factorial i))))


(defun concat [c1 c2]
    (if (= (size c2) 0)
        c1
        (concat (append c1 (head c2)) (tail c2))
    )
)

(defun map [f coll]
    (if (= (size coll) 0)
        coll
        (concat (append (list) (f (head coll))) (map f (tail coll)))
    )
)

(map printint
    (map factorial (append (append (append (list) 3) 4) 5)
    ))

(defun doNothing [forms] forms)

(doNothing ! printint ((fn [i] (+ i 1)) 2) )

(defun macroIsList [form] (head form))
(defun macroContents [form]  (head (tail (tail form))))
(defun macroCreteListForm [forms] (append(append(append (list) true) false) forms))
(defun macroCreateStringForm [s] (append (append (append (list) false) false) s))

(defun rev [l]
    (if (= (size l) 0)
        (list)
        (append (rev (tail l)) (head l))
    )
)

(ps "MACRO DEMO\n")

(defun listOf_ [oldForms]
    (if (= (size oldForms) 0)
        (macroCreteListForm (append (list) (macroCreateStringForm "list") ))
        (macroCreteListForm (append (append (append (list) (macroCreateStringForm "append")) (listOf_ (tail oldForms)) ) (head oldForms)))
    )
)

(defun listOf [forms] (
    listOf_ (rev (macroContents forms))
))


(map printint
    (map factorial (listOf ! 3 4 5)
    ))