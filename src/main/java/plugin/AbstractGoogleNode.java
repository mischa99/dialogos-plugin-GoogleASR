package plugin;

import com.clt.diamant.ExecutionStoppedException;
import com.clt.diamant.Grammar;
import com.clt.diamant.InputCenter;
import com.clt.diamant.Resources;
import com.clt.diamant.graph.nodes.AbstractInputNode;
import com.clt.diamant.graph.nodes.NodeExecutionException;
import com.clt.diamant.graph.ui.EdgeConditionModel;
import com.clt.diamant.gui.NodePropertiesDialog;
import com.clt.diamant.gui.ScriptEditorDialog;
import com.clt.gui.CmdButton;
import com.clt.gui.ComponentRenderer;
import com.clt.gui.GUI;
import com.clt.gui.OptionPane;
import com.clt.gui.table.TableRowDragger;
import com.clt.gui.table.TextRenderer;
import com.clt.script.debug.DefaultDebugger;
import com.clt.speech.Language;
import com.clt.speech.recognition.LanguageName;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.*;

public abstract class AbstractGoogleNode extends AbstractInputNode {
    private static ImageIcon micInv = null;
    private static ImageIcon micOn = null;
    private static ImageIcon micOff = null;

    protected static final String ENABLE_GARBAGE = "enableGarbage";
    private static Object DIRECT_GRAMMAR = new Object() {
        public String toString() {
            return Resources.getString("DirectGrammar");
        }
    };
    private static Object DYNAMIC_GRAMMAR = new Object() {
        public String toString() {
            return Resources.getString("DynamicGrammar");
        }
    };

    public static Object SELECTED_LANGUAGE = null;
    public static boolean SELECTED_INTERIM_RESULTS = false;
    public static boolean SELECTED_SINGLE_UTTERANCE = false;

    @Override
    public JComponent createEditorComponent(final Map<String, Object> properties) {
        List<LanguageName> languages = new ArrayList(this.getAvailableLanguages());
        if (!languages.contains(properties.get("language"))) {
            languages.add(1, (LanguageName)properties.get("language"));
        }

        languages.add(0, new LanguageName(Resources.getString("DefaultLanguage"), (Language)null));
        JTabbedPane tabs = GUI.createTabbedPane();
        final JPanel p = new JPanel(new BorderLayout());
        final EdgeConditionModel edgeModel = new EdgeConditionModel(this, properties, Resources.getString("InputPatterns"));
        Vector<Object> grammars = new Vector();
        grammars.add(DIRECT_GRAMMAR);
        grammars.add(DYNAMIC_GRAMMAR);
        List<com.clt.diamant.Grammar> definedGrammars = this.getGraph().getOwner().getGrammars();
        if (!definedGrammars.isEmpty()) {
            grammars.add(new JSeparator());
            grammars.addAll(definedGrammars);
        }

        final JComboBox language = NodePropertiesDialog.createComboBox(properties, "language", languages);
        final JComboBox grammar = new JComboBox(grammars);
        grammar.setRenderer(new ComponentRenderer());
        final JButton editGrammar = new CmdButton(Resources.getString("Edit"), new Runnable() {
            public void run() {
                Object selection = grammar.getSelectedItem();
                if (selection instanceof Grammar) {
                    Grammar g = (Grammar)selection;
                    ScriptEditorDialog.editGrammar(p, g);
                } else if (selection == AbstractGoogleNode.DYNAMIC_GRAMMAR) {
                    String gx = (String)properties.get("grammarExpression");
                    JTextArea a = new JTextArea();
                    a.setWrapStyleWord(true);
                    a.setLineWrap(true);
                    a.setText(gx);
                    a.setEditable(true);
                    JScrollPane jsp = new JScrollPane(a, 22, 31);
                    jsp.setPreferredSize(new Dimension(400, 200));
                    OptionPane.message(p, jsp, Resources.getString("Expression"), -1);
                    properties.put("grammarExpression", a.getText());
                }

            }
        });
        grammar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                language.setEnabled(true);
                if (grammar.getSelectedItem() instanceof com.clt.diamant.Grammar) {
                    com.clt.diamant.Grammar g = (com.clt.diamant.Grammar)grammar.getSelectedItem();
                    properties.put("grammar", g);

                    try {
                        String lang = AbstractGoogleNode.this.compileGrammar(properties, (List)null).getLanguage();
                        if (lang != null) {
                            LanguageName grammarLanguage = null;
                            Language l = new Language(lang);
                            List<LanguageName> languages = AbstractGoogleNode.this.getAvailableLanguages();
                            Iterator var7 = languages.iterator();

                            LanguageName ln;
                            while(var7.hasNext()) {
                                ln = (LanguageName)var7.next();
                                if (ln.getLanguage().equals(l)) {
                                    grammarLanguage = ln;
                                    language.setSelectedItem(SELECTED_LANGUAGE);
                                    break;
                                }
                            }

                            if (grammarLanguage == null) {
                                var7 = languages.iterator();

                                while(var7.hasNext()) {
                                    ln = (LanguageName)var7.next();
                                    if (ln.getLanguage().getLocale().getLanguage().equals(l.getLocale().getLanguage())) {
                                        grammarLanguage = ln;
                                        language.setSelectedItem(SELECTED_LANGUAGE);
                                        break;
                                    }
                                }
                            }

                            if (grammarLanguage != null) {
                                language.setSelectedItem(grammarLanguage);
                                language.setSelectedItem(SELECTED_LANGUAGE);
                            }
                            language.setSelectedItem(SELECTED_LANGUAGE);
                            language.setEnabled(false);
                        }
                    } catch (Exception var9) {
                    }

                    editGrammar.setText(Resources.getString("EditGrammar"));
                    editGrammar.setEnabled(true);
                    edgeModel.setName(Resources.getString("InputPatterns"));
                } else if (grammar.getSelectedItem() == AbstractGoogleNode.DIRECT_GRAMMAR) {
                    properties.remove("grammar");
                    properties.remove("grammarExpression");
                    editGrammar.setText(Resources.getString("Edit"));
                    editGrammar.setEnabled(false);
                    edgeModel.setName(Resources.getString("InputWords"));
                } else if (grammar.getSelectedItem() == AbstractGoogleNode.DYNAMIC_GRAMMAR) {
                    properties.remove("grammar");
                    properties.putIfAbsent("grammarExpression", "");
                    editGrammar.setText(Resources.getString("EditExpression"));
                    editGrammar.setEnabled(true);
                    edgeModel.setName(Resources.getString("InputPatterns"));
                }

            }
        });
        com.clt.diamant.Grammar g = (com.clt.diamant.Grammar)properties.get("grammar");
        if (g != null) {
            grammar.setSelectedItem(g);
        } else if (this.getProperty("grammarExpression") != null) {
            grammar.setSelectedItem(DYNAMIC_GRAMMAR);
        } else {
            grammar.setSelectedItem(DIRECT_GRAMMAR);
        }

        JPanel header = new JPanel(new GridBagLayout());
        header.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gbc.gridy = 0;
        gbc.fill = 0;
        gbc.anchor = 17;
        gbc.insets = new Insets(3, 3, 3, 3);
        header.add(new JLabel(Resources.getString("Grammar") + ':'), gbc);
        ++gbc.gridx;
        gbc.weightx = 1.0D;
        header.add(grammar, gbc);
        gbc.weightx = 0.0D;
        ++gbc.gridx;
        header.add(editGrammar, gbc);
        gbc.gridx = 0;
        ++gbc.gridy;
        header.add(new JLabel(Resources.getString("Language") + ':'), gbc);
        ++gbc.gridx;
        gbc.weightx = 1.0D;
        header.add(language, gbc);
        p.add(header, "North");
        final JTable table = new JTable(edgeModel);
        final JScrollPane jsp = GUI.createScrollPane(table, new Dimension(300, 150));
        table.getTableHeader().setReorderingAllowed(false);
        TableRowDragger.addDragHandler(table);
        TableColumn column = table.getColumnModel().getColumn(0);
        column.setCellRenderer(new TextRenderer() {
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!table.isEnabled()) {
                    label.setForeground(jsp.getBackground());
                    label.setBackground(jsp.getBackground());
                } else if (!edgeModel.isCellEditable(row, column)) {
                    label.setForeground(Color.lightGray);
                }

                return label;
            }
        });
        JPanel center = new JPanel(new BorderLayout());
        center.add(jsp, "Center");
        p.add(center, "Center");
        JPanel buttons = new JPanel(new FlowLayout(2));
        final JButton deleteButton = new CmdButton(new Runnable() {
            public void run() {
                if (!table.isEditing() || table.getCellEditor().stopCellEditing()) {
                    edgeModel.deleteRows(table.getSelectedRows());
                }

            }
        }, Resources.getString("Delete"));
        buttons.add(deleteButton);
        deleteButton.setEnabled(table.getSelectedRow() >= 0);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                deleteButton.setEnabled(table.getSelectedRow() >= 0);
            }
        });
        final JButton newButton = new CmdButton(new Runnable() {
            public void run() {
                if (!table.isEditing() || table.getCellEditor().stopCellEditing()) {
                    int row = edgeModel.addRow();
                    table.setRowSelectionInterval(row, row);
                }

            }
        }, Resources.getString("New"));
        buttons.add(newButton);
        p.add(buttons, "South");
        p.add(Box.createHorizontalStrut(8), "West");
        p.add(Box.createHorizontalStrut(8), "East");
        final JTextField tf = NodePropertiesDialog.createTextField(properties, "timeout");
        final JCheckBox timeout = new JCheckBox(Resources.getString("Timeout") + ':');
        final JCheckBox forceTimeout = NodePropertiesDialog.createCheckBox(properties, "forceTimeout", Resources.getString("forceTimeout"));
        timeout.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent evt) {
                tf.setEnabled(timeout.isSelected());
                forceTimeout.setEnabled(timeout.isSelected());
                if (timeout.isSelected()) {
                    tf.selectAll();
                } else {
                    properties.remove("timeout");
                    forceTimeout.setSelected(false);
                }

            }
        });
        timeout.setSelected(properties.get("timeout") != null);
        forceTimeout.setEnabled(timeout.isSelected());
        JPanel options = new JPanel(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.weightx = 0.0D;
        gbc.weighty = 0.0D;
        gbc.insets = new Insets(3, 12, 3, 0);
        gbc.fill = 0;
        gbc.anchor = 17;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        options.add(timeout, gbc);
        ++gbc.gridx;
        options.add(tf, gbc);
        ++gbc.gridy;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        Insets insets = gbc.insets;
        gbc.insets = new Insets(0, insets.left + 12, insets.bottom, insets.right);
        options.add(forceTimeout, gbc);
        gbc.insets = insets;
        ++gbc.gridy;
        gbc.weighty = 0.0D;
        gbc.weightx = 1.0D;
        gbc.gridwidth = 2;
        JCheckBox background = NodePropertiesDialog.createCheckBox(properties, "background", Resources.getString("background"));
        final Runnable updater = new Runnable() {
            public void run() {
                boolean bg = (Boolean)properties.get("background");
                table.setEnabled(!bg);
                newButton.setEnabled(!bg);
                if (bg) {
                    deleteButton.setEnabled(false);
                }

            }
        };
        background.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                boolean bg = e.getStateChange() == 1;
                if (bg) {
                    edgeModel.clear();
                    edgeModel.addRow();
                }

                updater.run();
            }
        });
        updater.run();
        options.add(background, gbc);
        ++gbc.gridy;
        gbc.weighty = 0.0D;
        gbc.weightx = 1.0D;
        gbc.gridwidth = 2;
        JCheckBox robustness = NodePropertiesDialog.createCheckBox(properties, "enableGarbage", Resources.getString("enableGarbage"));
        options.add(robustness, gbc);
        ++gbc.gridy;
        gbc.weighty = 0.0D;
        gbc.weightx = 1.0D;
        gbc.gridwidth = 2;





        //!!!
        ///NEW PARAMETERS HERE
        JCheckBox interimResults = NodePropertiesDialog.createCheckBox(properties, "allow Interim Results", Resources.getString("allow Interim Results"));
        options.add(interimResults, gbc);
        ++gbc.gridy;
        gbc.weighty = 0.0D;
        gbc.weightx = 1.0D;
        gbc.gridwidth = 2;

        interimResults.setSelected(true);
        SELECTED_INTERIM_RESULTS = true;
        interimResults.addItemListener(new ItemListener() {

            public void itemStateChanged(ItemEvent evt) {
                //interimResults.setEnabled(timeout.isSelected());

                if (interimResults.isSelected()) {
                    SELECTED_INTERIM_RESULTS = true;
                } else {
                    interimResults.setSelected(false);
                    SELECTED_INTERIM_RESULTS = false;
                }

            }
        });

        JCheckBox singleUtterance = NodePropertiesDialog.createCheckBox(properties, "only single Utterance", Resources.getString("only single Utterence"));
        options.add(singleUtterance, gbc);
        ++gbc.gridy;
        gbc.weighty = 0.0D;
        gbc.weightx = 1.0D;
        gbc.gridwidth = 2;
        singleUtterance.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent evt) {
                //singleUtterence.setEnabled(singleUtterence.isSelected());
                if (singleUtterance.isSelected() & !(interimResults.isSelected())) {
                    SELECTED_SINGLE_UTTERANCE = true;
                }
                else {
                    singleUtterance.setSelected(false);
                    SELECTED_SINGLE_UTTERANCE = false;
                }

            }
        });




        JPanel threshold = new JPanel(new FlowLayout(0, 6, 6));
        threshold.add(new JLabel(Resources.getString("threshold")));
        threshold.add(NodePropertiesDialog.createLongField(properties, "threshold", 0L, 100L));
        threshold.add(new JLabel("%"));
        options.add(threshold, gbc);
        ++gbc.gridy;
        gbc.weighty = 1.0D;
        gbc.weightx = 1.0D;
        gbc.gridwidth = 2;
        gbc.insets = insets;
        options.add(Box.createVerticalGlue(), gbc);
        final JButton test = new JButton(Resources.getString("TryRecognition"));
        test.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new Thread(new Runnable() {
                    public void run() {
                        if (table.getCellEditor() != null) {
                            table.getCellEditor().stopCellEditing();
                        }

                        RootPaneContainer rpc = (RootPaneContainer)GUI.getParent(test, RootPaneContainer.class);

                        try {
                            AbstractGoogleNode.this.recognizeExec(rpc.getLayeredPane(), new DefaultDebugger(), (InputCenter)null, properties, true);
                        } catch (ExecutionStoppedException var3) {
                        } catch (NodeExecutionException var4) {
                            if (var4.getException() != null) {
                                OptionPane.error(test, var4.getException());
                            } else {
                                OptionPane.error(test, var4);
                            }
                        } catch (ThreadDeath var5) {
                            throw var5;
                        } catch (Throwable var6) {
                            var6.printStackTrace();
                            OptionPane.error(test, var6);
                        }

                    }
                }).start();
            }
        });
        JPanel mainPage = new JPanel(new BorderLayout(6, 0));
        mainPage.add(p, "Center");
        JPanel tryRec = new JPanel(new FlowLayout(1));
        tryRec.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
        tryRec.add(test);
        mainPage.add(tryRec, "South");
        tabs.addTab(Resources.getString("RecognitionTab"), mainPage);
        tabs.addTab(Resources.getString("Options"), options);
        return tabs;

    }

}
