package aQute.lib.data;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

public class Data {

	public static String validate(Object o) throws Exception {
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb);
		try {
			Field fields[] = o.getClass().getFields();
			for (Field f : fields) {
				Validator patternValidator = f.getAnnotation(Validator.class);
				Numeric numericValidator = f.getAnnotation(Numeric.class);
				AllowNull allowNull = f.getAnnotation(AllowNull.class);
				Object value = f.get(o);
				if (value == null) {
					if (allowNull == null)
						formatter.format("Value for %s must not be null%n", f.getName());
				} else {

					if (patternValidator != null) {
						Pattern p = Pattern.compile(patternValidator.value());
						Matcher m = p.matcher(value.toString());
						if (!m.matches()) {
							String reason = patternValidator.reason();
							if (reason.length() == 0)
								formatter.format("Value for %s=%s does not match pattern %s%n", f.getName(), value,
										patternValidator.value());
							else
								formatter.format("Value for %s=%s %s%n", f.getName(), value, reason);
						}
					}

					if (numericValidator != null) {
						if (o instanceof String) {
							try {
								o = Double.parseDouble((String) o);
							}
							catch (Exception e) {
								formatter.format("Value for %s=%s %s%n", f.getName(), value, "Not a number");
							}
						}

						try {
							Number n = (Number) o;
							long number = n.longValue();
							if (number >= numericValidator.min() && number < numericValidator.max()) {
								formatter.format("Value for %s=%s not in valid range (%s,%s]%n", f.getName(), value,
										numericValidator.min(), numericValidator.max());
							}
						}
						catch (ClassCastException e) {
							formatter.format("Value for %s=%s [%s,%s) is not a number%n", f.getName(), value,
									numericValidator.min(), numericValidator.max());
						}
					}
				}
			}
			if (sb.length() == 0)
				return null;

			if (sb.length() > 0)
				sb.delete(sb.length() - 1, sb.length());
			return sb.toString();
		}
		finally {
			formatter.close();
		}
	}

	public static void details(Object data, Appendable out) throws Exception {
		Field fields[] = data.getClass().getFields();
		Formatter formatter = new Formatter(out);
		try {
			for (Field f : fields) {
				String name = f.getName();
				name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
				formatter.format("%-40s %s%n", name, f.get(data));
			}
		}
		finally {
			formatter.close();
		}
	}
}
