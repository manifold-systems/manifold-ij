/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.core;

import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import manifold.util.ReflectUtil;

import java.util.function.Supplier;

import static com.intellij.psi.impl.source.tree.ElementType.EXPRESSION_BIT_SET;

public interface ManElementType extends JavaElementType
{
  IElementType TUPLE_VALUE_EXPRESSION = (IElementType)ReflectUtil.constructor(
      JavaElementType.class.getTypeName() + "\$JavaCompositeElementType", String.class, Supplier.class )
    .newInstance( "TUPLE_VALUE_EXPRESSION", (Supplier<?>)() -> new ManPsiTupleValueExpressionImpl() );
  IElementType TUPLE_EXPRESSION = (IElementType)ReflectUtil.constructor(
      JavaElementType.class.getTypeName() + "\$JavaCompositeElementType", String.class, Supplier.class )
    .newInstance( "TUPLE_EXPRESSION", (Supplier<?>)() -> new ManPsiTupleExpressionImpl() );

  boolean init = init();
  static boolean init()
  {
    // add tuple expression types to EXPRESSION_BIT_SET
    ReflectUtil.field( ElementType.class, "EXPRESSION_BIT_SET" ).setStatic(
      TokenSet.orSet( EXPRESSION_BIT_SET, TokenSet.create( TUPLE_VALUE_EXPRESSION, TUPLE_EXPRESSION ) ) );
    return true;
  }
}
