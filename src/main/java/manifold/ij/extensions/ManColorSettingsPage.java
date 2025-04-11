package manifold.ij.extensions;

import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class ManColorSettingsPage implements ColorSettingsPage
{
    static final ColorKey KEY_PREPROCESSOR_DIRECTIVE = ColorKey.createColorKey( "KEY_PREPROCESSOR_DIRECTIVE", DefaultLanguageHighlighterColors.KEYWORD.getDefaultAttributes().getForegroundColor() );
    static final ColorKey KEY_PREPROCESSOR_MASKED_CODE = ColorKey.createColorKey( "KEY_PREPROCESSOR_MASKED_CODE", DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT.getDefaultAttributes().getBackgroundColor() );
    private static final ColorDescriptor[] COLOR_DESCRIPTORS = new ColorDescriptor[] {
            new ColorDescriptor("Directives", KEY_PREPROCESSOR_DIRECTIVE, ColorDescriptor.Kind.FOREGROUND ),
            new ColorDescriptor("Masked code", KEY_PREPROCESSOR_MASKED_CODE, ColorDescriptor.Kind.BACKGROUND ),
    };


    @Override
    public @NotNull AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return new AttributesDescriptor[0]; // not using this since all colors are specific to foreground or background, not both, see getColorDescriptors() below
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return COLOR_DESCRIPTORS;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Preprocessor"; // This shows up in the Settings dialog under: Editor | Color Scheme | Preprocessor
    }

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new JavaFileHighlighter();
    }

    @Override
    public @NonNls @NotNull String getDemoText() {
        return "To see the effect of your changes, check an open editor that uses the preprocessor."; // Doesn't render correctly, so showing a message instead :/
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

}
