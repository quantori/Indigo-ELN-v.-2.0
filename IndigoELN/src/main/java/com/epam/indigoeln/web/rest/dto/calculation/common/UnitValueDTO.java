package com.epam.indigoeln.web.rest.dto.calculation.common;

public class UnitValueDTO {

    private double value;
    private String unit;
    private boolean entered;

    public UnitValueDTO() {
    }

    public UnitValueDTO(double value, String unit, boolean entered) {
        this.value = value;
        this.unit = unit;
        this.entered = entered;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public boolean isEntered() {
        return entered;
    }

    public void setEntered(boolean entered) {
        this.entered = entered;
    }
}
