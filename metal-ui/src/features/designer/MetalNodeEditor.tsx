import {
    useCallback,
    useMemo,
    useRef,
    useState,
} from "react";
import {
    Button,
    Container,
    Grid,
    IconButton,
    Paper,
    Skeleton,
    Stack,
    TextField,
} from "@mui/material";
import { RJSFSchema } from "@rjsf/utils";
import { IChangeEvent } from "@rjsf/core";
import { Form } from "@rjsf/mui";
import validator from "@rjsf/validator-ajv8";
import { Metal } from "../../model/Metal";
import { MetalNodeProps } from "./MetalView";
import { VscArrowLeft } from "react-icons/vsc";
import { ResizeBackdrop } from "../ui/ResizeBackdrop";
import { useMetalFlow, useMutableMetalNodeEditor } from "./DesignerProvider";
import { Mutable } from "../../model/Mutable";

export interface MetalNodeEditorProps {}

export interface MetalNodeEditorHandler {
    load: (props: MetalNodeProps) => void;
    close: () => void;
}

export const metalNodeEditorHandlerInitial: MetalNodeEditorHandler = {
    load: (props: MetalNodeProps) => {},
    close: () => {},
};

export class MutableMetalNodeEditorHandler
    extends Mutable<MetalNodeEditorHandler>
    implements MetalNodeEditorHandler
{
    load(props: MetalNodeProps) {
        this.get().load(props);
    }
    close() {
        this.get().close();
    }
}

export const MetalNodeEditor = (props: MetalNodeEditorProps) => {
    const metalFlowHandler = useMetalFlow();
    const [metalProps, setMetalProps] = useState<MetalNodeProps | null>(null);
    const [isOpen, setOpen] = useState(false);
    const nameInputRef = useRef<HTMLInputElement>();
    const handler: MutableMetalNodeEditorHandler = useMutableMetalNodeEditor();

    const printInputs = () => {
        const inputs = metalFlowHandler.inputs;
        if (metalProps === null) {
            return;
        }
        console.log(inputs(metalProps.metal.id));
    };

    const load = useCallback((props: MetalNodeProps) => {
        setMetalProps(props);
        setOpen(true);
    }, []);

    const close = useCallback(() => {
        setMetalProps(null);
        setOpen(false);
    }, []);

    useMemo(() => {
        handler.set({
            load: load,
            close: close,
        });
    }, [close, handler, load]);

    if (metalProps == null) {
        return <Skeleton></Skeleton>;
    }
    const metal = metalProps.metal;
    const schema = metalProps.metalPkg.formSchema;
    const uiSchema = metalProps.metalPkg.uiSchema;

    const onConfirm = (data: IChangeEvent<any, RJSFSchema, any>) => {
        let newName = metal.name;
        if (nameInputRef !== null && nameInputRef.current !== undefined) {
            newName = nameInputRef.current.value;
        }
        const newMetal: Metal = {
            ...metal,
            name: newName,
            props: data.formData,
        };
        metalProps.onUpdate(newMetal);
        setMetalProps(null);
        setOpen(false);
    };

    const onCancel = () => {
        setMetalProps(null);
        setOpen(false);
    };

    return (
        <ResizeBackdrop open={isOpen} backgroundColor={"#f4f4f4"} opacity={"1"}>
            <div
                style={{
                    position: "absolute",
                    boxSizing: "border-box",
                    margin: "0px",
                    width: "100%",
                    height: "100%",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "flex-start",
                    justifyContent: "flex-start",
                }}
            >
                <Paper
                    square
                    variant="outlined"
                    sx={{
                        boxSizing: "border-box",
                        margin: "0px",
                        width: "100%",
                        height: "5vh",
                        display: "flex",
                        flexDirection: "row",
                        alignContent: "space-between",
                        justifyContent: "flex-start",
                    }}
                >
                    <IconButton onClick={onCancel}>
                        <VscArrowLeft />
                    </IconButton>
                </Paper>
                <Grid
                    container
                    spacing={1}
                    sx={{
                        boxSizing: "border-box",
                        margin: "0px",
                        position: "absolute",
                        left: "0px",
                        right: "0px",
                        top: "5.5vh",
                        bottom: "1vh",
                    }}
                >
                    <Grid item xs={3}>
                        <Paper
                            sx={{
                                height: "100%",
                                width: "100%",
                            }}
                        ></Paper>
                    </Grid>
                    <Grid item xs={9}>
                        <Paper
                            sx={{
                                position: "absolute",
                                height: "-webkit-fill-available",
                                width: "-webkit-fill-available",
                                overflowY: "hidden",
                            }}
                        >
                            <Container
                                sx={{
                                    margin: "0px",
                                    height: "100%",
                                    overflowY: "auto",
                                    display: "block",
                                }}
                            >
                                <Stack
                                    direction="column"
                                    justifyContent="flex-start"
                                    alignItems="flex-start"
                                    spacing={2}
                                >
                                    <div></div>
                                    <TextField
                                        id={"name"}
                                        label={"name"}
                                        defaultValue={metal.name}
                                        inputRef={nameInputRef}
                                    />
                                    <Form
                                        schema={schema}
                                        uiSchema={uiSchema}
                                        validator={validator}
                                        formData={metalProps.metal.props}
                                        onSubmit={onConfirm}
                                    >
                                        <Stack
                                            direction="row"
                                            justifyContent="flex-start"
                                            alignItems="center"
                                            spacing={8}
                                        >
                                            <Button type={"submit"} variant={"contained"}>
                                                {"confirm"}
                                            </Button>

                                            <Button onClick={printInputs}>{"inputs"}</Button>
                                        </Stack>
                                    </Form>
                                    <div></div>
                                </Stack>
                            </Container>
                        </Paper>
                    </Grid>
                </Grid>
            </div>
        </ResizeBackdrop>
    );
};
