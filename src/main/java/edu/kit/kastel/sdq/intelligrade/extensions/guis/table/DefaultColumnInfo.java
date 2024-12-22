/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis.table;

import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nullable;

class DefaultColumnInfo extends ColumnInfo {
    private final Class<?> columnClass;

    public DefaultColumnInfo(String name, Class<?> columnClass) {
        super(name);

        this.columnClass = columnClass;
    }

    @Override
    public @Nullable Object valueOf(Object object) {
        return object;
    }

    @Override
    public java.lang.Class<?> getColumnClass() {
        return this.columnClass;
    }
}
