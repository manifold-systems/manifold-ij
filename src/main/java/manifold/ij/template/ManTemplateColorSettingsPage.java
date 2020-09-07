package manifold.ij.template;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.*;
import manifold.rt.api.DisableStringLiteralTemplates;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Map;

public class ManTemplateColorSettingsPage implements ColorSettingsPage
{
  private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
    new AttributesDescriptor( "Comment", ManTemplateHighlighter.COMMENT ),
    new AttributesDescriptor( "Delimiter", ManTemplateHighlighter.DELIMITER ),
  };

  @Nullable
  @Override
  public Icon getIcon()
  {
    return ManTemplateIcons.FILE;
  }

  @NotNull
  @Override
  public SyntaxHighlighter getHighlighter()
  {
    return new ManTemplateHighlighter();
  }

  @NotNull
  @Override
  @DisableStringLiteralTemplates
  public String getDemoText()
  {
    return "<%@ params(String name)%>\n" +
           "The quick brown fox jumps over the lazy dog.\n" +
           "<% java-code %>" +
           "<%=name%> sells seashells by the seashore\n" +
           "<% java-code %>\n" +
           "<%-- Good, better, best. Never rest until good be better and better best --%>\n" +
           "My name is ${name}\n";
  }

  @Nullable
  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap()
  {
    return null;
  }

  @NotNull
  @Override
  public AttributesDescriptor[] getAttributeDescriptors()
  {
    return DESCRIPTORS;
  }

  @NotNull
  @Override
  public ColorDescriptor[] getColorDescriptors()
  {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String getDisplayName()
  {
    return "Manifold Template";
  }
}