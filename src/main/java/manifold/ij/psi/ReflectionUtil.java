package manifold.ij.psi;

import com.intellij.openapi.diagnostic.Logger;
import java.lang.reflect.Field;

/**
 */
public class ReflectionUtil
{
  private static final Logger LOG = Logger.getInstance( ReflectionUtil.class.getName() );

  public static <T, R> void setFinalFieldPerReflection( Class<T> clazz, T instance, Class<R> oldClazz, R newValue )
  {
    try
    {
      for( Field field : clazz.getDeclaredFields() )
      {
        if( field.getType().equals( oldClazz ) )
        {
          field.setAccessible( true );
          field.set( instance, newValue );
          break;
        }
      }
    }
    catch( IllegalArgumentException | IllegalAccessException x )
    {
      LOG.error( x );
    }
  }
}

