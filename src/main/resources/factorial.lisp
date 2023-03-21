(defunc printint [i] <<C
    printf("%d\n", i->value.i);
    return i;
C
)

(defunc printints [i i2] <<C
    printf("%d %d\n", i->value.i, i2->value.i);
    gc__dec_ref_counter(i2);
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

(ps "factorial demo\n")
(printint (factorial 5))
(printint (factorial 6))

(ps "clojure demo (subber)\n")
(printint ((subber 1) 10))
(printint ((subber 2) 10))

(ps "lambda demo\n")
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
(ps "list demo\n")
(map printint
    (map factorial (append (append (append (list) 3) 4) 5)
    ))



(defun macroIsList [form] (head form))
(defun macroContents [form]  (head (tail (tail form))))
(defun macroCreteListForm [forms] (append(append(append (list) true) false) forms))
(defun macroCreteArgsForm [forms] (append(append(append (list) true) true) forms))
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

(ps "LET DEMO\n")

(defun let_ [binds statement]
    (if (= (size binds) 0)
        statement
        (macroCreteListForm (listOf !
            (macroCreteListForm (listOf!
                (macroCreateStringForm "fn")
                (macroCreteArgsForm (listOf ! (head binds)))
                (let_ (tail (tail binds)) statement)
            ))
            (head (tail binds))
        ))
    )
)

(defun let [forms]
    (let_  (macroContents (head (macroContents forms)))  (head (tail (macroContents forms))) ))


(let! [
    a 1
    b 2
    c (+ a b)
    c (factorial c)
] (printint c))


(ps "gc invalid UAF\n")
(defun addTwoNums [a b] (
    let! [c (+ a b)] (fn [i] (+ c i))
))

(printint ((addTwoNums 100 20) 3))


(ps "filter example\n")

(defun pred [a]
    (= a 0))

(defun filter [f coll]
    (if (= (size coll) 0)
            (list)
            (if (f (head coll))
                (concat (append (list) (head coll)) (filter f (tail coll)))
                (concat (list) (filter f (tail coll)))
                )
        )
)

(map printint
    (filter (fn [c] (= c 0)) (listOf ! 2 3 0 3 3 0))
    )

(ps "reduce test\n")

(defun reducei [f coll v]
    (if (= (size coll) 0)
        v
        (reducei f (tail coll) (f v (head coll)))
    )
   )

(defun reduce [f coll]
    (reducei f (tail coll) (head coll))
    )

(printint (reduce (fn [a b] (+ a b)) (listOf ! 1 2 3)))

(defun delist [arrs]
    (reducei
        concat
        (tail arrs)
        (head arrs)
        )
)


(defun range [from to]
    (if (= from to)
        (list)
        (concat (append (list) from) (range (+ from 1) to))
    )
)

(defun ends_with [col elem]
    (= (head (rev col)) elem)
)

(defun step [alphabet words]
    (delist
        (map
            (fn [word]
                (map
                    (fn [letter]
                        (concat word letter)
                    )
                    (filter
                        (fn [a]
                           (not (ends_with word (head a)))
                        )
                        alphabet
                    )
                )
            )
            words
        )
    )
)


(defun task [alphabet size]
    (reducei
        (fn [acc x] (step alphabet acc))
        (range 0 (- size 1))
        alphabet
    )
)

(ps "t\n")

(print(task (listOf ! "a" "b" "c") 3))

(defunc scanf_int[] <<C
    int t;
    scanf(" %d", &t);
    return lisp__int_constructor(t);
C
)
(defunc scanf_char [] <<C
    char t;
    scanf(" %c", &t);
    return lisp__char_constructor(t);
C
)
(defun read_strs [t]
     (if (= t 0)
          (list)
          (concat (listOf !(listOf ! (scanf_char))) (read_strs (- t 1)))
     )
)
(defun stdin[]
    (let ! [
        len (scanf_int)
        alp_len (scanf_int)
        alphabet (read_strs alp_len)
    ]
        (task alphabet len)
    )
)
(print (stdin))