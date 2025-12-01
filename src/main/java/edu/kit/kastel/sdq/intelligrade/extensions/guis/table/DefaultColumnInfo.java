/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis.table;

import com.intellij.util.ui.ColumnInfo;
import org.jspecify.annotations.Nullable;

class DefaultColumnInfo extends ColumnInfo<Object, Object> {
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
    public Class<?> getColumnClass() {
        return this.columnClass;
    }
}
