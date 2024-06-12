package com.gradle.develocity.core;

import com.jakewharton.picnic.Cell;
import com.jakewharton.picnic.CellStyle;
import com.jakewharton.picnic.TableSection;
import com.jakewharton.picnic.TextAlignment;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.stream;

@SuppressWarnings("UnusedReturnValue")
final class Table {

    private final String title;
    private String[] header;
    private final List<String[]> rows = new ArrayList<>();

    private Table(String title) {
        this.title = title;
    }

    static Table withTitle(String title) {
        return new Table(title);
    }

    Table header(Object... header) {
        this.header = stream(header).map(String::valueOf).toArray(String[]::new);
        return this;
    }

    Table row(Object... values) {
        this.rows.add(stream(values).map(String::valueOf).toArray(String[]::new));
        return this;
    }

    @Override
    public String toString() {
        return new com.jakewharton.picnic.Table.Builder()
                .setCellStyle(buildCellStyle())
                .setHeader(buildHeader())
                .setBody(buildRows())
                .build()
                .toString();
    }

    private CellStyle buildCellStyle() {
        return new CellStyle.Builder().setBorder(true).setPaddingLeft(1).setPaddingRight(1).build();
    }

    private TableSection buildHeader() {
        return new TableSection.Builder().addRow(buildTitle()).addRow(header).build();
    }

    private Cell buildTitle() {
        return new Cell.Builder(title).setStyle(new CellStyle.Builder().setAlignment(TextAlignment.MiddleCenter).build()).setColumnSpan(header.length).build();
    }

    private TableSection buildRows() {
        final var table = new TableSection.Builder();
        rows.forEach(table::addRow);
        return table.build();
    }
}
