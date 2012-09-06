/*
 * Copyright 2012 DTO Labs, Inc. (http://dtolabs.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/*
* TestBaseWorkflowStrategy.java
* 
* User: Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
* Created: 9/11/12 2:15 PM
* 
*/
package com.dtolabs.rundeck.core.execution.workflow;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.FrameworkProject;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.*;
import com.dtolabs.rundeck.core.execution.commands.*;
import com.dtolabs.rundeck.core.execution.dispatch.Dispatchable;
import com.dtolabs.rundeck.core.execution.dispatch.DispatcherResult;
import com.dtolabs.rundeck.core.execution.service.NodeExecutorResult;
import com.dtolabs.rundeck.core.tools.AbstractBaseTest;
import com.dtolabs.rundeck.core.utils.FileUtils;
import com.dtolabs.rundeck.core.utils.NodeSet;
import org.apache.tools.ant.BuildListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;


/**
 * TestBaseWorkflowStrategy is ...
 *
 * @author Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
 */
public class TestBaseWorkflowStrategy extends AbstractBaseTest {

    public static final String TEST_PROJECT="TestBaseWorkflowStrategy";
    Framework testFramework;
    String testnode;

    public TestBaseWorkflowStrategy(String name) {
        super(name);
    }

    protected void setUp() {
        super.setUp();
        testFramework = getFrameworkInstance();
        testnode = testFramework.getFrameworkNodeName();
        final FrameworkProject frameworkProject = testFramework.getFrameworkProjectMgr().createFrameworkProject(
            TEST_PROJECT);
        File resourcesfile = new File(frameworkProject.getNodesResourceFilePath());
        //copy test nodes to resources file
        try {
            FileUtils.copyFileStreams(new File("src/test/resources/com/dtolabs/rundeck/core/common/test-nodes1.xml"),
                                      resourcesfile);
        } catch (IOException e) {
            throw new RuntimeException("Caught Setup exception: " + e.getMessage(), e);
        }
    }

    protected void tearDown() throws Exception {
        super.tearDown();

        File projectdir = new File(getFrameworkProjectsBase(), TEST_PROJECT);
        FileUtils.deleteDir(projectdir);
    }

    public static class testWorkflowStrategy extends BaseWorkflowStrategy{
        private WorkflowExecutionResult result;
        private int execIndex=0;
        private List<Object> results;
        private List<Map<String,Object>> inputs;

        public testWorkflowStrategy(Framework framework) {
            super(framework);
            inputs = new ArrayList<Map<String, Object>>();
            results = new ArrayList<Object>();
        }

        @Override
        public WorkflowExecutionResult executeWorkflowImpl(ExecutionContext executionContext,
                                                           WorkflowExecutionItem item) {

            return result;
        }

        protected boolean executeWFItem(final ExecutionContext executionContext,
                                        final Map<Integer, Object> failedMap,
                                        final List<DispatcherResult> resultList,
                                        final int c,
                                        final ExecutionItem cmd, final boolean keepgoing) throws
                                                                                          WorkflowStepFailureException {
            HashMap<String,Object> input=new HashMap<String, Object>();
            input.put("context", executionContext);
            input.put("failedMap", failedMap);
            input.put("resultList", resultList);
            input.put("c", c);
            input.put("cmd", cmd);
            input.put("keepgoing", keepgoing);
            inputs.add(input);

            int ndx=execIndex++;
            final Object o = results.get(ndx);
            if(o instanceof Boolean){
                return (Boolean)o;
            }else if(o instanceof WorkflowStepFailureException) {
                throw (WorkflowStepFailureException) o;
            }else if(o instanceof String) {
                throw new WorkflowStepFailureException((String) o, new ExecutionResult() {
                    public Exception getException() {
                        return null;
                    }

                    public DispatcherResult getResultObject() {
                        return null;
                    }

                    public boolean isSuccess() {
                        return false;
                    }
                }, c);
            }else {
                fail("Unexpected result at index " + ndx + ": " + o);
                return false;
            }
        }

        public WorkflowExecutionResult getResult() {
            return result;
        }

        public void setResult(WorkflowExecutionResult result) {
            this.result = result;
        }

        public List<Object> getResults() {
            return results;
        }

        public void setResults(List<Object> results) {
            this.results = results;
        }

        public List<Map<String, Object>> getInputs() {
            return inputs;
        }
    }

    void assertExecWFItems(final List<ExecutionItem> items,
                           final boolean wfKeepgoing,
                           final List<Map<String, Object>> expected,
                           final boolean expectedSuccess,
                           final List<Object> returnResults, final boolean expectStepException)
         {

        //test success 1 item
        final NodeSet nodeset = new NodeSet();
             final com.dtolabs.rundeck.core.execution.ExecutionContext context =
                 new ExecutionContextImpl.Builder()
                     .frameworkProject(TEST_PROJECT)
                     .user("user1")
                     .nodeSelector(nodeset)
                     .executionListener(new testListener())
                     .framework(testFramework)
                     .build();

        testWorkflowStrategy strategy = new testWorkflowStrategy(testFramework);

        strategy.getResults().addAll(returnResults);


        final Map<Integer, Object> map = new HashMap<Integer, Object>();
        final List<DispatcherResult> resultList = new ArrayList<DispatcherResult>();
        final boolean keepgoing = wfKeepgoing;

        boolean itemsSuccess=false;
        boolean sawException=false;
        try {
            itemsSuccess = strategy.executeWorkflowItemsForNodeSet(context,
                                                                                 map,
                                                                                 resultList,
                                                                                 items,
                                                                                 keepgoing);
            assertFalse(expectStepException);
        } catch (WorkflowStepFailureException e) {
            assertTrue("Unexpected step exception: " + e.getMessage(), expectStepException);
            e.printStackTrace();
            sawException = true;
        }

        assertEquals(expectStepException, sawException);
        assertEquals(expectedSuccess, itemsSuccess);

        assert expected.size() == strategy.getInputs().size();
        int i=0;
        for (final Map<String, Object> expectedMap : expected) {
            final Map<String, Object> map1 = strategy.getInputs().get(i);
            assertEquals("ExpectedMap index " + i + " value c",expectedMap.get("c"), map1.get("c"));
            assertEquals("ExpectedMap index " + i + " value cmd",expectedMap.get("cmd"), map1.get("cmd"));
            assertEquals("ExpectedMap index "+i+" value keepgoing",expectedMap.get("keepgoing"), map1.get("keepgoing"));
            i++;
        }

    }
    private List<ExecutionItem> mkTestItems(ExecutionItem... item) {
        return Arrays.asList(item);
    }

    public void testExecuteWorkflowItemsForNodeSet() throws Exception {
        {
            //test success 1 item

            final testWorkflowCmdItem testCmd1 = new testWorkflowCmdItem();

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", false);

            assertExecWFItems(
                mkTestItems(testCmd1),
                false,
                Arrays.asList(expectResult1),
                true,
                Arrays.asList((Object) true), false
            );


        }


        {

            //test failure 1 item no keepgoing

            final testWorkflowCmdItem testCmd1 = new testWorkflowCmdItem();

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", false);

            assertExecWFItems(
                mkTestItems(testCmd1),
                false,
                Arrays.asList(expectResult1),
                false,
                Arrays.asList((Object) false), false
            );
        }

        {
            //test failure 1 exception no keepgoing


            final testWorkflowCmdItem testCmd1 = new testWorkflowCmdItem();

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", false);

            assertExecWFItems(
                mkTestItems(testCmd1),
                false,
                Arrays.asList(expectResult1),
                false,
                Arrays.asList((Object) "Failure"),
                true
            );

        }
        {
            //test failure 1 exception yes keepgoing


            final testWorkflowCmdItem testCmd1 = new testWorkflowCmdItem();

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", true);

            assertExecWFItems(
                mkTestItems(testCmd1),
                true,
                Arrays.asList(expectResult1),
                false,
                Arrays.asList((Object) "Failure"), false
            );

        }

    }

    public void testExecuteWorkflowItemsForNodeSetFailureHandler() throws Exception {
        {
            //test success 1 item, no failure handler


            final testHandlerWorkflowCmdItem testCmd1 = new testHandlerWorkflowCmdItem();
            testCmd1.failureHandler = null;

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", false);

            assertExecWFItems(
                mkTestItems(testCmd1),
                false,
                Arrays.asList(expectResult1),
                true,
                Arrays.asList((Object) true), false
            );

        }


        {
            //test failure 1 item no keepgoing, with failure handler (keepgoing=false)


            final testHandlerWorkflowCmdItem testCmd1 = new testHandlerWorkflowCmdItem();
            testCmd1.failureHandler = null;
            final testWorkflowCmdItem testCmdHandler1 = new testWorkflowCmdItem();
            testCmd1.failureHandler = testCmdHandler1;
            testCmdHandler1.keepgoingOnSuccess = false;

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", false);
            final Map<String, Object> expectResult2 = new HashMap<String, Object>();
            expectResult2.put("c", 1);
            expectResult2.put("cmd", testCmdHandler1);
            expectResult2.put("keepgoing", false);

            assertExecWFItems(
                mkTestItems(testCmd1),
                false,
                Arrays.asList(expectResult1,expectResult2),
                false,
                Arrays.asList((Object) false,true),//item1 fails, handler succeeds
                false
            );


        }

        {
            //test failure 1 item no keepgoing throw exception, with failure handler (keepgoing=false)


            final testHandlerWorkflowCmdItem testCmd1 = new testHandlerWorkflowCmdItem();
            testCmd1.failureHandler = null;
            final testWorkflowCmdItem testCmdHandler1 = new testWorkflowCmdItem();
            testCmd1.failureHandler = testCmdHandler1;
            testCmdHandler1.keepgoingOnSuccess = false;

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", false);
            final Map<String, Object> expectResult2 = new HashMap<String, Object>();
            expectResult2.put("c", 1);
            expectResult2.put("cmd", testCmdHandler1);
            expectResult2.put("keepgoing", false);

            assertExecWFItems(
                mkTestItems(testCmd1),
                false,
                Arrays.asList(expectResult1, expectResult2),
                false,
                Arrays.asList((Object) "Failure", true),//item1 fails, handler succeeds
                true
            );

        }
    }
    public void testExecuteWorkflowItemsForNodeSetFailureHandlerKeepgoing() throws Exception {
        {
            //test failure 1 item yes keepgoing, with failure handler (keepgoing=false)


            final testHandlerWorkflowCmdItem testCmd1 = new testHandlerWorkflowCmdItem();
            testCmd1.failureHandler = null;
            final testWorkflowCmdItem testCmdHandler1 = new testWorkflowCmdItem();
            testCmd1.failureHandler = testCmdHandler1;
            testCmdHandler1.keepgoingOnSuccess = false;

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", true);
            final Map<String, Object> expectResult2 = new HashMap<String, Object>();
            expectResult2.put("c", 1);
            expectResult2.put("cmd", testCmdHandler1);
            expectResult2.put("keepgoing", false);

            assertExecWFItems(
                mkTestItems(testCmd1),
                true,
                Arrays.asList(expectResult1, expectResult2),
                true,
                Arrays.asList((Object) false, true),//item1 fails, handler succeeds
                false
            );

        }

        {
            //test failure 1 item yes keepgoing throw exception, with failure handler (keepgoing=false)


            final testHandlerWorkflowCmdItem testCmd1 = new testHandlerWorkflowCmdItem();
            testCmd1.failureHandler = null;
            final testWorkflowCmdItem testCmdHandler1 = new testWorkflowCmdItem();
            testCmd1.failureHandler = testCmdHandler1;
            testCmdHandler1.keepgoingOnSuccess = false;

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", true);
            final Map<String, Object> expectResult2 = new HashMap<String, Object>();
            expectResult2.put("c", 1);
            expectResult2.put("cmd", testCmdHandler1);
            expectResult2.put("keepgoing", false);

            assertExecWFItems(
                mkTestItems(testCmd1),
                true,
                Arrays.asList(expectResult1, expectResult2),
                true,
                Arrays.asList((Object) "Failure", true),//item1 fails, handler succeeds
                false
            );

        }

        {
            //test failure 2 items yes keepgoing throw exception, with failure handler (keepgoing=false)
            final testHandlerWorkflowCmdItem testCmd1 = new testHandlerWorkflowCmdItem();
            testCmd1.failureHandler = null;
            final testWorkflowCmdItem testCmdHandler1 = new testWorkflowCmdItem();
            testCmd1.failureHandler = testCmdHandler1;
            testCmdHandler1.keepgoingOnSuccess = false;
            final testWorkflowCmdItem testCmd2 = new testWorkflowCmdItem();

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", true);
            final Map<String, Object> expectResult2 = new HashMap<String, Object>();
            expectResult2.put("c", 1);
            expectResult2.put("cmd", testCmdHandler1);
            expectResult2.put("keepgoing", false);
            final Map<String, Object> expectResult3 = new HashMap<String, Object>();
            expectResult3.put("c", 2);
            expectResult3.put("cmd", testCmd2);
            expectResult3.put("keepgoing", true);

            assertExecWFItems(
                mkTestItems(testCmd1, testCmd2),
                true,
                Arrays.asList(expectResult1, expectResult2, expectResult3),
                true,
                Arrays.asList((Object) "Failure",true, true),//item1 fails, handler succeeds, item2 succeeds
                false
            );

        }
        {
            //test failure 2 items yes keepgoing throw exception, with failure handler (keepgoing=false)

            final testHandlerWorkflowCmdItem testCmd1 = new testHandlerWorkflowCmdItem();
            testCmd1.failureHandler = null;
            final testWorkflowCmdItem testCmdHandler1 = new testWorkflowCmdItem();
            testCmd1.failureHandler = testCmdHandler1;
            testCmdHandler1.keepgoingOnSuccess = false;
            final testWorkflowCmdItem testCmd2 = new testWorkflowCmdItem();

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", true);
            final Map<String, Object> expectResult2 = new HashMap<String, Object>();
            expectResult2.put("c", 1);
            expectResult2.put("cmd", testCmdHandler1);
            expectResult2.put("keepgoing", false);
            final Map<String, Object> expectResult3 = new HashMap<String, Object>();
            expectResult3.put("c", 2);
            expectResult3.put("cmd", testCmd2);
            expectResult3.put("keepgoing", true);

            assertExecWFItems(
                mkTestItems(testCmd1, testCmd2),
                true,
                Arrays.asList(expectResult1, expectResult2, expectResult3),
                false,
                Arrays.asList((Object) "Failure", true, false),//item1 fails, handler succeeds, item2 fails
                false
            );

        }
    }

    public void testExecuteWorkflowItemsForNodeSetFailureHandlerKeepgoingOnSuccess() throws Exception {

        {
            //test failure 1 item no keepgoing, with failure handler (keepgoing=true)
            final testHandlerWorkflowCmdItem testCmd1 = new testHandlerWorkflowCmdItem();
            testCmd1.failureHandler = null;
            final testWorkflowCmdItem testCmdHandler1 = new testWorkflowCmdItem();
            testCmd1.failureHandler = testCmdHandler1;
            testCmdHandler1.keepgoingOnSuccess = true;
            final testWorkflowCmdItem testCmd2 = new testWorkflowCmdItem();

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", false);
            final Map<String, Object> expectResult2 = new HashMap<String, Object>();
            expectResult2.put("c", 1);
            expectResult2.put("cmd", testCmdHandler1);
            expectResult2.put("keepgoing", false);
            final Map<String, Object> expectResult3 = new HashMap<String, Object>();
            expectResult3.put("c", 2);
            expectResult3.put("cmd", testCmd2);
            expectResult3.put("keepgoing", false);

            assertExecWFItems(
                mkTestItems(testCmd1, testCmd2),
                false,
                Arrays.asList(expectResult1, expectResult2, expectResult3),
                true,
                Arrays.asList((Object) false, true, true),//item1 fails, handler succeeds, item2 succeeds
                false
            );

        }
        {
            //test failure 1 item no keepgoing, with failure handler (keepgoing=true) fails
            final testHandlerWorkflowCmdItem testCmd1 = new testHandlerWorkflowCmdItem();
            testCmd1.failureHandler = null;
            final testWorkflowCmdItem testCmdHandler1 = new testWorkflowCmdItem();
            testCmd1.failureHandler = testCmdHandler1;
            testCmdHandler1.keepgoingOnSuccess = true;
            final testWorkflowCmdItem testCmd2 = new testWorkflowCmdItem();

            final Map<String, Object> expectResult1 = new HashMap<String, Object>();
            expectResult1.put("c", 1);
            expectResult1.put("cmd", testCmd1);
            expectResult1.put("keepgoing", false);
            final Map<String, Object> expectResult2 = new HashMap<String, Object>();
            expectResult2.put("c", 1);
            expectResult2.put("cmd", testCmdHandler1);
            expectResult2.put("keepgoing", false);

            assertExecWFItems(
                mkTestItems(testCmd1, testCmd2),
                false,
                Arrays.asList(expectResult1, expectResult2),
                false,
                Arrays.asList((Object) false, false, "should not be executed"),//item1 fails, handler fails, item2 succeeds
                false
            );

        }

    }


    static class testWorkflowCmdItem implements HandlerExecutionItem {
        private String type;
        int flag = -1;
        boolean keepgoingOnSuccess;

        public boolean isKeepgoingOnSuccess() {
            return keepgoingOnSuccess;
        }


        @Override
        public String toString() {
            return "testWorkflowCmdItem{" +
                   "type='" + type + '\'' +
                   ", flag=" + flag +
                   ", keepgoingOnSuccess=" + keepgoingOnSuccess +
                   '}';
        }

        public String getType() {
            return type;
        }
    }

    static class testHandlerWorkflowCmdItem implements ExecutionItem,HasFailureHandler {
        private String type;
        private ExecutionItem failureHandler;
        int flag = -1;

        @Override
        public String toString() {
            return "testHandlerWorkflowCmdItem{" +
                   "type='" + type + '\'' +
                   ", flag=" + flag +
                   ", failureHandler=" + failureHandler +
                   '}';
        }

        public String getType() {
            return type;
        }

        public ExecutionItem getFailureHandler() {
            return failureHandler;
        }
    }

    static class testInterpreter implements CommandInterpreter {
        List<ExecutionItem> executionItemList = new ArrayList<ExecutionItem>();
        List<ExecutionContext> executionContextList = new ArrayList<ExecutionContext>();
        List<INodeEntry> nodeEntryList = new ArrayList<INodeEntry>();
        int index = 0;
        List<InterpreterResult> resultList = new ArrayList<InterpreterResult>();
        boolean shouldThrowException = false;

        public InterpreterResult interpretCommand(ExecutionContext executionContext,
                                                  ExecutionItem executionItem, INodeEntry iNodeEntry) throws
                                                                                                      InterpreterException {
            executionItemList.add(executionItem);
            executionContextList.add(executionContext);
            nodeEntryList.add(iNodeEntry);
            if (shouldThrowException) {
                throw new InterpreterException("testInterpreter test exception");
            }
            System.out.println("return index: (" + index + ") in size: " + resultList.size());
            return resultList.get(index++);
        }
    }


    static class testListener implements ExecutionListenerOverride {
        public boolean isTerse() {
            return false;
        }

        public String getLogFormat() {
            return null;
        }

        public void log(int i, String s) {
            System.err.println(i + ": " + s);
        }

        public FailedNodesListener getFailedNodesListener() {
            return null;
        }

        public void beginExecution(ExecutionContext context, ExecutionItem item) {
        }

        public void finishExecution(ExecutionResult result, ExecutionContext context, ExecutionItem item) {
        }

        public void beginNodeExecution(ExecutionContext context, String[] command, INodeEntry node) {
        }

        public void finishNodeExecution(NodeExecutorResult result, ExecutionContext context, String[] command,
                                        INodeEntry node) {
        }

        public void beginNodeDispatch(ExecutionContext context, ExecutionItem item) {
        }

        public void beginNodeDispatch(ExecutionContext context, Dispatchable item) {
        }

        public void finishNodeDispatch(DispatcherResult result, ExecutionContext context, ExecutionItem item) {
        }

        public void finishNodeDispatch(DispatcherResult result, ExecutionContext context, Dispatchable item) {
        }

        public void beginFileCopyFileStream(ExecutionContext context, InputStream input, INodeEntry node) {
        }

        public void beginFileCopyFile(ExecutionContext context, File input, INodeEntry node) {
        }

        public void beginFileCopyScriptContent(ExecutionContext context, String input, INodeEntry node) {
        }

        public void finishFileCopy(String result, ExecutionContext context, INodeEntry node) {
        }

        public void beginInterpretCommand(ExecutionContext context, ExecutionItem item, INodeEntry node) {
        }

        public void finishInterpretCommand(InterpreterResult result, ExecutionContext context, ExecutionItem item,
                                           INodeEntry node) {
        }

        public BuildListener getBuildListener() {
            return null;
        }

        public ExecutionListenerOverride createOverride() {
            return this;
        }

        public void setTerse(boolean terse) {
        }

        public void setLogFormat(String format) {
        }

        public void setFailedNodesListener(FailedNodesListener listener) {
        }
    }

}
