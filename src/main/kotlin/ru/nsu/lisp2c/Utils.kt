package ru.nsu.lisp2c

const val lispObjectType = "lisp__object *"
const val newObject = "gc__new_object"
const val varPrefix = "lisp_var"
fun nameCName(name: String) = "${varPrefix}_$name"
fun cNameClojureType(cname: String) = cname.replaceFirst(varPrefix, "lisp_clojure") + "_t"
fun cNameBody(cname: String) = cname.replaceFirst(varPrefix, "lisp_body")
fun cNameStartLabel(cname: String) = cname.replaceFirst(varPrefix, "lisp_start")
fun functionType(n: Int) = "lisp_fun_${n}_t"
