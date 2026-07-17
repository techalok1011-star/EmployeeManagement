package com.empmgmt.dto;

public class PartySuggestionDTO {
    private String name;
    private String gst;
    private String combined;
    private String trailingNumber;

    public PartySuggestionDTO() {}

    public PartySuggestionDTO(String name, String gst, String combined, String trailingNumber) {
        this.name = name;
        this.gst = gst;
        this.combined = combined;
        this.trailingNumber = trailingNumber;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGst() { return gst; }
    public void setGst(String gst) { this.gst = gst; }

    public String getCombined() { return combined; }
    public void setCombined(String combined) { this.combined = combined; }

    public String getTrailingNumber() { return trailingNumber; }
    public void setTrailingNumber(String trailingNumber) { this.trailingNumber = trailingNumber; }
}
