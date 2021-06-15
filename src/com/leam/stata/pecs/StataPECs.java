package com.leam.stata.pecs;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

import com.stata.sfi.Data;
import com.stata.sfi.Frame;
import com.stata.sfi.Missing;
import com.stata.sfi.SFIToolkit;

public class StataPECs {
	
	public static int getNota(String args[]) {
		String pec = args[0];
		long obs = Long.parseLong(args[1]);
		int rc = 0;
		
		try {
	        // abrir el formulario de la PEC
			File file = new File(pec);
			PDDocument pdf = PDDocument.load(file);
		    PDAcroForm form = pdf.getDocumentCatalog().getAcroForm();
			
		    // obtener el frame con los datos de la solución
		    Frame sol = Frame.connect("sol");
			int iP = sol.getVarIndex("p"); 
			int iW = sol.getVarIndex("w"); 
			int iT = sol.getVarIndex("tipo");
			long total = sol.getObsTotal();
			for (long i = 1; i <= total; i++ ) {
				String p = sol.getStr(iP, i);
				PDField f = form.getField(p);
            	String r = ""; 
        		if (f instanceof PDTextField) {
        			PDTextField ed = (PDTextField) f;				// text field: numérico
        			r = ed.getValue();
        		}
        		if (f instanceof PDComboBox) {
        			PDComboBox co = (PDComboBox) f;					// combobox field: respuesta cerrada (A,B,.. o val. prof.)
        			r = co.getValue().get(0);
        		}
        		Data.storeStr(Data.getVarIndex(p),obs,r);
        		
        		Double w = sol.getNum(iW,i);						// w
        		Double tipo = sol.getNum(iT,i);						// tipo de respuesta
        		Double punt = (double) 0;
        		if (tipo==1) {
        			// valoración del profesor
        			punt = Double.parseDouble(r.replace(",", ".")) * w;
        		} else {
        			String rescor = sol.getStr(sol.getVarIndex("rescor"), i); 	// respuesta correcta
        			// tokenizar por si hay más de 1 respuesta correcta
        			String[] respuestas = rescor.split(";");
        			for (String resp : respuestas) {
        				if (tipo==2) {
        					// respuesta cerrada
        					if (r.trim().equalsIgnoreCase(resp.trim())) {
        						punt = w;
        					}
        				}
        				if (tipo==3) {
        					// respuesta numérica
        					try {  
        						if (Double.parseDouble(r.replace(",",".")) == Double.parseDouble(resp.replace(",","."))) {
        							punt = w;
        						}
        					} catch(NumberFormatException e){
        						// si no se puede convertir en número, la respuesta es errónea
        						punt = (double) 0;
        					}
        				}        				
        			}
        		}
        		Data.storeNum(Data.getVarIndex(p.replace("P", "w")),obs,punt);
			}
		} catch (Exception e) {
			SFIToolkit.errorln("error getting PEC data");
			SFIToolkit.errorln("(" + e.getMessage() + ")");
			return (198);
		}
		
		return (rc);
	}

	public static int getHonor(String args[]) {
		String file = args[0];
		String dni = args[1];
		int rc = 0;
		
		try {
	        // open pdf form
			PDDocument pdf = PDDocument.load(new File(file));
		    PDAcroForm form = pdf.getDocumentCatalog().getAcroForm();
	        if (form.getFields().size()>0) {
            	PDCheckBox honor = (PDCheckBox) form.getField("HONOR");
            	if (honor.isChecked()) {
            		SFIToolkit.executeCommand("qui replace honor = 1 if DNI==\""+dni+"\"",false);
            	} else {
            		SFIToolkit.executeCommand("qui replace honor = 0 if DNI==\""+dni+"\"",false);            		
            	}
	        } else {
	        	SFIToolkit.executeCommand("qui replace problema = 1 if DNI==\""+dni+"\"",false);
	        }
		} catch (Exception e) {
			SFIToolkit.errorln("error getting PEC data");
			SFIToolkit.errorln("(" + e.getMessage() + ")");
			return (198);
		}

		return (rc);
	}

	public static int getSintaxis(String args[]) {
		String orig = args[0];
		String sint = args[1];
		String cd = args[2];
		int rc = 0;
		
		try {
	        // obtener todos los archivos pdf de orig
	        File folder = new File(orig);
	        FilenameFilter pdfFilter = (File dir1, String name) -> { return name.toLowerCase().endsWith(".pdf"); };
	        File[] PECs = folder.listFiles(pdfFilter);

			int iP = Data.getVarIndex("p"); 
			int itipo = Data.getVarIndex("tipo");
			int iacc = Data.getVarIndex("accion"); 
			int ip1 = Data.getVarIndex("p1"); 
			int ip2 = Data.getVarIndex("p2"); 
			long total = Data.getObsTotal();			
			String curso = Data.getStrf(Data.getVarIndex("curso"), 1);
	        for (File file : PECs) {
	            if (file.isFile()) {
	                List<String> lines = new ArrayList<>();
	                
	            	// dni del nombre del archivo
	                String name = file.getName();
	                String dni = name.substring(name.lastIndexOf("_")+1,name.lastIndexOf("."));
	                lines.add("/* PEC: " + dni);
	    	        
	                // abrir form pdf
	    			PDDocument pdf = PDDocument.load(file);
	    		    PDAcroForm form = pdf.getDocumentCatalog().getAcroForm();
	    	        if (form.getFields().size()>0) {
	                    if (!form.getField("COMENT").getValueAsString().isEmpty()) {
	                    	// comentarios, si hay
	                        lines.add(form.getField("COMENT").getValueAsString());
	                    }
		                lines.add("*/");
		                // carpeta de trabajo
		                lines.add("cd " + cd + "\n");
		                // en el curso ST2, eliminar archivos previos
		                if (curso.equalsIgnoreCase("ST2")) {
		                	String c = Data.getStrf(ip2,1);
		                	if (c.contains(";")) {
		                		c = c.split(";")[1];
		                		String[] del = c.split(" ");
		                		for (String d : del) {
		                			lines.add("capture erase " + d + ".dta");
		                		}
		                	}
		                	lines.add("scalar drop _all\n");
	                		lines.add("clear\n");
		                }
		                
		    			for (long i = 1; i <= total; i++ ) {
		    				String p = Data.getStr(iP, i);
		    				Double tipo = Data.getNum(itipo, i);
		    				if (tipo==1) {
		    					Double accion = Data.getNum(iacc, i);
		    					String n = p.substring(1,3);
	    						// si la acción no es 4 - Ignorar, procesamos
		    					if (accion!=4) {
		    						lines.add("** Pregunta "+n);
			    					if (accion==1 || accion==2) {
			    						// si la acción es 1 Leer o 2 Test, obtener el contenido del campo
				    					PDField f = form.getField("M"+n+"_01");
			    						String c = f.getValueAsString();
			    						if (c.trim().length()>0) {
				    						lines.add(c+"\n");
				    						// si acción es 2 Test, añadir el código para comprobar
				    						if (accion==2) {
				    							lines.add("preserve");
				    							String t = ( Data.getStr(ip1,i).indexOf("_") >= 0 ? "* _*" : "*" );
				    							lines.add("rename " + t + ", lower");
				    							lines.add("capture noisily cf " + Data.getStr(ip1,i) +
				    									" using " + Data.getStr(ip2,i) + ", all verbose");
				    							lines.add("restore\n");
				    						}
			    						}
			    					}
			    					if (accion==3) {
			    						// si la acción es 3 Datos, añadir el código para capturar datos
			    						String f = Data.getStr(ip1, i);
			    						String ext = f.substring(f.lastIndexOf(".")+1);
			    						if (ext.equalsIgnoreCase("xlsx")) {
			    							lines.add("import excel " + f + ", sheet(" + Data.getStr(ip2, i).split(";")[0] +
			    									") firstrow clear\n");
			    						}
			    						if (ext.equalsIgnoreCase("dta")) {
			    							lines.add("use " + f + ", clear\n");
			    						}
			    					}
			    					if (accion==5) {
			    						// si la acción es 5 Grabar, añadir el save
			    						String f = Data.getStr(ip1, i);
		    							lines.add("save " + f + ", replace\n");			    							
			    					}
		    					}
		    				}
		    			}
	    	        }
	    	        // grabar archivo do
	    	        Files.write(Paths.get(sint+ "/" + dni + ".do"), lines, Charset.forName("UTF-8"));
	    	        
	            	SFIToolkit.displayln("Procesando PEC: " + dni);
	            }
	        }
		} catch (Exception e) {
			SFIToolkit.errorln("error getting PEC data");
			SFIToolkit.errorln("(" + e.getMessage() + ")");
			return (198);
		}

		return (rc);
	}

	public static int getPEC1(String args[]) {
		String pec = args[0];
		long obs = Long.parseLong(args[1]);
		int preguntas = Integer.parseInt(args[2]);
		int rc = 0;
		
		try {
			File f = new File(pec);
			if(f.exists() && !f.isDirectory()) {
				// si existe el archivo, pec entregada
				Data.storeNum(Data.getVarIndex("ePEC1"), obs, 1);
				
                // abrir form pdf
    			PDDocument pdf = PDDocument.load(f);
    		    PDAcroForm form = pdf.getDocumentCatalog().getAcroForm();
    	        if (form.getFields().size()>0) {
                	PDCheckBox honor = (PDCheckBox) form.getField("HONOR");
                	if (honor.isChecked()) Data.storeNum(Data.getVarIndex("hPEC1"), obs, 1);
                	else Data.storeNum(Data.getVarIndex("hPEC1"), obs, 0);
            		            		
            		int j = 64 + preguntas;
            		for (int i=65; i<=j; i++) {
            			String n = "P01_"+String.valueOf((char)i);
            			String r = "R01_"+String.valueOf((char)i);
            			if (!form.getField(n).getValueAsString().isEmpty()) {
            				String v = form.getField(n).getValueAsString().replace(",", ".");
            				Data.storeNum(Data.getVarIndex(r), obs, Double.parseDouble(v));
            			} else {
            				Data.storeNum(Data.getVarIndex(r), obs, Missing.getValue());
            			}
            		}
    	        }
    	        
    	        form = null;
    	        pdf.close();
    	        pdf = null;
			} else {
				// si no existe el archivo, no se ha entregado la pec
				Data.storeNum(Data.getVarIndex("ePEC1"), obs, 0);
			}
		} catch (Exception e) {
			SFIToolkit.errorln("error getting PEC1 data");
			SFIToolkit.errorln("(" + e.getMessage() + ")");
			return (198);
		}

		return (rc);
	}

	
	public static void main(String args[]) {
		// DO NOTHING
	}
}
