package org.jabref.logic.exporter;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.paint.Color;

import org.jabref.logic.util.MetadataSerializationConfiguration;
import org.jabref.logic.util.io.FileUtil;
import org.jabref.model.groups.AbstractGroup;
import org.jabref.model.groups.AllEntriesGroup;
import org.jabref.model.groups.AutomaticGroup;
import org.jabref.model.groups.AutomaticKeywordGroup;
import org.jabref.model.groups.AutomaticPersonsGroup;
import org.jabref.model.groups.ExplicitGroup;
import org.jabref.model.groups.GroupTreeNode;
import org.jabref.model.groups.KeywordGroup;
import org.jabref.model.groups.RegexKeywordGroup;
import org.jabref.model.groups.SearchGroup;
import org.jabref.model.groups.SmartGroup;
import org.jabref.model.groups.TexGroup;
import org.jabref.model.search.SearchFlags;
import org.jabref.model.strings.StringUtil;

public class GroupSerializer {
    private static String serializeAllEntriesGroup() {
        return MetadataSerializationConfiguration.ALL_ENTRIES_GROUP_ID;
    }

    private String serializeSmartGroup(SmartGroup group) {
        StringBuilder sb = new StringBuilder();
        sb.append(MetadataSerializationConfiguration.SMART_GROUP_ID);
        sb.append(StringUtil.quote(group.getName(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(group.getHierarchicalContext().ordinal());
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);

        appendGroupDetails(sb, group);

        return sb.toString();
    }

    private String serializeExplicitGroup(ExplicitGroup group) {
        StringBuilder sb = new StringBuilder();
        sb.append(MetadataSerializationConfiguration.EXPLICIT_GROUP_ID);
        sb.append(StringUtil.quote(group.getName(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(group.getHierarchicalContext().ordinal());
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);

        appendGroupDetails(sb, group);

        return sb.toString();
    }

    private String serializeKeywordGroup(KeywordGroup group) {
        boolean isRegex = group instanceof RegexKeywordGroup;
        StringBuilder sb = new StringBuilder();
        sb.append(MetadataSerializationConfiguration.KEYWORD_GROUP_ID);
        sb.append(StringUtil.quote(group.getName(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(group.getHierarchicalContext().ordinal());
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(StringUtil.quote(group.getSearchField().getName(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(StringUtil.quote(group.getSearchExpression(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(StringUtil.booleanToBinaryString(group.isCaseSensitive()));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(StringUtil.booleanToBinaryString(isRegex));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);

        appendGroupDetails(sb, group);

        return sb.toString();
    }

    private String serializeSearchGroup(SearchGroup group) {
        StringBuilder sb = new StringBuilder();
        sb.append(MetadataSerializationConfiguration.SEARCH_GROUP_ID);
        sb.append(StringUtil.quote(group.getName(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(group.getHierarchicalContext().ordinal());
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(StringUtil.quote(group.getSearchExpression(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(StringUtil.booleanToBinaryString(group.getSearchFlags().contains(SearchFlags.CASE_SENSITIVE)));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(StringUtil.booleanToBinaryString(group.getSearchFlags().contains(SearchFlags.REGULAR_EXPRESSION)));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);

        appendGroupDetails(sb, group);

        return sb.toString();
    }

    private void appendGroupDetails(StringBuilder builder, AbstractGroup group) {
        builder.append(StringUtil.booleanToBinaryString(group.isExpanded()));
        builder.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        builder.append(group.getColor().map(Color::toString).orElse(""));
        builder.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        builder.append(group.getIconName().orElse(""));
        builder.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        builder.append(group.getDescription().orElse(""));
        builder.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
    }

    /**
     * Returns a textual representation of this node and its children. This
     * representation contains both the tree structure and the textual
     * representations of the group associated with each node.
     * Every node is one entry in the list of strings.
     *
     * @return a representation of the tree based at this node as a list of strings
     */
    public List<String> serializeTree(GroupTreeNode node) {
        List<String> representation = new ArrayList<>();

        // Append current node
        representation.add(String.valueOf(node.getLevel()) + ' ' + serializeGroup(node.getGroup()));

        // Append children
        for (GroupTreeNode child : node.getChildren()) {
            representation.addAll(serializeTree(child));
        }

        return representation;
    }

    private String serializeGroup(AbstractGroup group) {
        return switch (group) {
            case AllEntriesGroup _ -> serializeAllEntriesGroup();
            case SmartGroup smartGroup -> serializeSmartGroup(smartGroup);
            case ExplicitGroup explicitGroup -> serializeExplicitGroup(explicitGroup);
            case KeywordGroup keywordGroup -> serializeKeywordGroup(keywordGroup);
            case SearchGroup searchGroup -> serializeSearchGroup(searchGroup);
            case AutomaticKeywordGroup keywordGroup -> serializeAutomaticKeywordGroup(keywordGroup);
            case AutomaticPersonsGroup personsGroup -> serializeAutomaticPersonsGroup(personsGroup);
            case TexGroup texGroup -> serializeTexGroup(texGroup);
            case null -> throw new IllegalArgumentException("Group cannot be null");
            default -> throw new UnsupportedOperationException("Don't know how to serialize group" + group.getClass().getName());
        };
    }

    private String serializeTexGroup(TexGroup group) {
        StringBuilder sb = new StringBuilder();
        sb.append(MetadataSerializationConfiguration.TEX_GROUP_ID);
        sb.append(StringUtil.quote(group.getName(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(group.getHierarchicalContext().ordinal());
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(StringUtil.quote(FileUtil.toPortableString(group.getFilePath()), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);

        appendGroupDetails(sb, group);

        return sb.toString();
    }

    private String serializeAutomaticPersonsGroup(AutomaticPersonsGroup group) {
        StringBuilder sb = new StringBuilder();
        sb.append(MetadataSerializationConfiguration.AUTOMATIC_PERSONS_GROUP_ID);
        appendAutomaticGroupDetails(sb, group);
        sb.append(StringUtil.quote(group.getField().getName(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        appendGroupDetails(sb, group);
        return sb.toString();
    }

    private void appendAutomaticGroupDetails(StringBuilder builder, AutomaticGroup group) {
        builder.append(StringUtil.quote(group.getName(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        builder.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        builder.append(group.getHierarchicalContext().ordinal());
        builder.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
    }

    private String serializeAutomaticKeywordGroup(AutomaticKeywordGroup group) {
        StringBuilder sb = new StringBuilder();
        sb.append(MetadataSerializationConfiguration.AUTOMATIC_KEYWORD_GROUP_ID);
        appendAutomaticGroupDetails(sb, group);
        sb.append(StringUtil.quote(group.getField().getName(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(StringUtil.quote(group.getKeywordDelimiter().toString(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        sb.append(StringUtil.quote(group.getKeywordHierarchicalDelimiter().toString(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR));
        sb.append(MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR);
        appendGroupDetails(sb, group);
        return sb.toString();
    }
}
