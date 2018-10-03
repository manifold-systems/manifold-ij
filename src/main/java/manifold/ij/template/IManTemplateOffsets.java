package manifold.ij.template;

import com.intellij.openapi.util.Key;
import java.util.List;

public interface IManTemplateOffsets
{
  Key<List<Integer>> EXPR_OFFSETS = Key.create( "EXPR_OFFSETS" );
  Key<List<Integer>> STMT_OFFSETS = Key.create( "STMT_OFFSETS" );
  Key<List<Integer>> DIRECTIVE_OFFSETS = Key.create( "DIRECTIVE_OFFSETS" );
}
