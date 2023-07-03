package org.kie.trustyai.external.interfaces;

import java.util.Map;

import jep.SubInterpreter;
import jep.python.PyCallable;
import jep.python.PyObject;

public abstract class ExternalPythonExplainer<T> extends ExternalPythonClass {

    public T invoke(Map<String, Object> arguments, SubInterpreter interpreter) {

        interpreter.exec("from " + getNamespace() + " import " + getName());
        final PyCallable callable = (PyCallable) interpreter.getValue(getName());
        final PyObject explainer;
        if (getConstructionArgs().isEmpty()) {
            explainer = (PyObject) callable.call();
        } else {
            explainer = (PyObject) callable.call(getConstructionArgs());
        }

        final PyCallable explain = (PyCallable) explainer.getAttr("explain");

        return (T) explain.call(arguments);
    }

}