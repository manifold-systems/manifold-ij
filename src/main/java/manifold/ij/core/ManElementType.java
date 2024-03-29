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

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import manifold.util.ReflectUtil;

import java.util.function.Supplier;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_DUMMY_ELEMENT;
import static com.intellij.psi.impl.source.tree.ElementType.EXPRESSION_BIT_SET;

public interface ManElementType extends JavaElementType
{
  boolean IS_2023_3_0__OR_LATER = ApplicationInfo.getInstance().getBuild().compareTo( BuildNumber.fromString( "233.8264.8" ) ) >= 0;
  IElementType TUPLE_VALUE_EXPRESSION = IS_2023_3_0__OR_LATER
    ? (IElementType)ReflectUtil.constructor(
        JavaElementType.class.getTypeName() + "\$JavaCompositeElementType", String.class, Supplier.class, IElementType.class )
        .newInstance( "TUPLE_VALUE_EXPRESSION", (Supplier<?>)() -> new ManPsiTupleValueExpressionImpl(), BASIC_DUMMY_ELEMENT )
    : (IElementType)ReflectUtil.constructor(
        JavaElementType.class.getTypeName() + "\$JavaCompositeElementType", String.class, Supplier.class )
        .newInstance( "TUPLE_VALUE_EXPRESSION", (Supplier<?>)() -> new ManPsiTupleValueExpressionImpl() );
  IElementType TUPLE_EXPRESSION = IS_2023_3_0__OR_LATER
    ? (IElementType)ReflectUtil.constructor(
        JavaElementType.class.getTypeName() + "\$JavaCompositeElementType", String.class, Supplier.class, IElementType.class )
        .newInstance( "TUPLE_EXPRESSION", (Supplier<?>)() -> new ManPsiTupleExpressionImpl(), BASIC_DUMMY_ELEMENT )
    : (IElementType)ReflectUtil.constructor(
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
